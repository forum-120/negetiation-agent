# A2A 谈判系统改造计划

## 总体目标

把当前单 JVM 内存调用的谈判模拟器，改造成分布式多 Agent 系统，聚焦三块能力：**真 A2A 协议通信 + 多谈判协议抽象 + 全链路可观测性**。

## 最终架构

```
        ┌──────────────────────┐
        │    Orchestrator      │  A2A 客户端 + ProtocolExecutor
        │        :8080         │  + PolicyEngine + RAG 接入
        └─────┬───────────┬────┘
              │ A2A       │ A2A
              ▼           ▼
      ┌───────────┐  ┌───────────┐
      │BuyerAgent │  │SellerAgent│
      │   :8081   │  │   :8082   │
      └───────────┘  └───────────┘

  Redis + Milvus（共享存储）     Jaeger（全链路追踪）
```

- **A2A 协议**：Agent 之间通信（Orchestrator ↔ Buyer / Seller）
- **多谈判协议**：Orchestrator 内 `ProtocolExecutor` 抽象，支持轮询 / sealed-bid / one-shot
- **RAG**：沿用现有方案，Orchestrator 在构建 Agent 上下文时接入 Milvus 策略库
- **可观测性**：OTel 自动跨服务传播，Jaeger 全链路 trace

---

## Phase 0 — 拆分前的准备

目的：把现在的单体代码梳理干净，把关键接口抽好，为后续分布式改造和协议扩展铺路。这一步不做跨进程，也不扩协议，只做代码层面的职责分离和物理隔离。

### 任务 0.1 引入 OpenTelemetry 骨架
在当前单体进程里先接入 tracing。需要埋点的关键 span 包括：一次完整谈判的 root span、每一轮的 child span、每次 Buyer/Seller Agent 调用、每次 RAG 检索（`StrategyKnowledgeService.searchStrategy`）、每次 LLM 底层调用。后端用 Jaeger 单机版。

**完成标志**：跑一次谈判，Jaeger 火焰图里能看到每轮耗时分布、LLM 调用延迟、RAG 召回时长。

### 任务 0.2 拆分业务逻辑与协议交互
现在 `NegotiationService.runNegotiationLoop` 里同时做了四件事：状态机控制、Agent 调用、RAG 拼接、异常记录。需要抽象出两个组件：
- `NegotiationOrchestrator`：只负责谈判流程控制（轮数、收敛判定、成交/失败决策）
- `AgentTransport` 接口：封装"向对方 Agent 发消息并拿回报价"，**方法返回 `CompletableFuture<OfferResponse>`**。这样 Phase 2 的 sealed-bid 协议可以直接并发调用 Buyer/Seller，不用再改接口

先实现一个 `InProcessTransport`，保持现有内存调用行为（同步场景下 future 立刻 complete 即可）。Orchestrator 从此不再关心对方是同进程还是跨网络、也不关心调用是串行还是并行——这就是 Phase 1 A2A 改造和 Phase 2 多协议扩展的共同前提。

**完成标志**：`NegotiationService` 瘦身 50% 以上，行为和改造前一致。

### 任务 0.3 用 Spring Profile 实现部署分离（替代多模块）
三个服务始终一起部署、共享大量 DTO，拆 Maven 多模块的 build 复杂度不值。改成：
- 保留单模块结构
- 新增 3 个 Profile：`orchestrator` / `buyer` / `seller`
- 关键 Bean 用 `@ConditionalOnProperty` 或 `@Profile` 控制加载（比如 Buyer Controller 只在 `buyer` Profile 下注册）
- 启动时通过 `--spring.profiles.active=buyer` 选择身份和端口

后续如果真的需要物理拆分（比如 Buyer 团队和 Seller 团队独立开发），再升级成多模块也不迟。

**完成标志**：同一份代码，用不同 Profile 起三个进程（端口 8080/8081/8082），行为和单体一致。

---

## Phase 1 — 上真 A2A 协议

目的：Buyer 和 Seller 变成独立的 Spring Boot 服务，通过spring ai的A2A协议

### 任务 1.1 Buyer / Seller 暴露 A2A 接口
每个 Agent 实现 A2A 最小集：
- **Agent Card**：`/.well-known/agent.json`，声明身份、能力、endpoint、鉴权方式
- **同步提交任务**：`POST /tasks/send`，JSON-RPC 2.0 格式
- **流式订阅**：`POST /tasks/sendSubscribe`，SSE 返回过程报价
- **状态机**：严格对齐 `submitted → working → input-required → completed`

### 任务 1.2 Orchestrator 改造成 A2A 客户端
把 `InProcessTransport` 替换成 `A2aHttpTransport`，所有对 Buyer/Seller 的调用走 HTTP。Orchestrator 本身也对外暴露 Agent Card，让整个系统对外看起来就是一个"谈判协调 Agent"。

### 任务 1.3 多服务部署
写 `docker-compose.yml` 拉起 buyer、seller、orchestrator、redis、milvus、jaeger 六个服务。确认 OTel 通过 W3C `traceparent` header 自动跨服务传播，Jaeger 里能看到连贯的跨服务 trace。

### 里程碑
一次 `docker compose up` 拉起全系统，Jaeger 里看到"orchestrator → buyer → llm → orchestrator → seller → llm"的完整跨服务火焰图。**这张截图放 GitHub README 首图。**

---

## Phase 2 — 多谈判协议抽象

目的：从"只会轮询砍价"升级成"支持多种谈判机制"，体现协议设计和系统抽象能力。这是本项目最出彩的一块。

### 任务 2.1 定义协议抽象
- `NegotiateProtocol` 枚举：`ITERATIVE_BARGAIN`（现有轮询）、`SEALED_BID`（密封出价）、`ONE_SHOT`（一次性 take-it-or-leave-it）
- `ProtocolExecutor` 接口：输入商品信息和双方 Agent 句柄，输出 `NegotiationResult`
- 每个协议一个实现类，各自维护独立状态机
- Orchestrator 根据请求参数选择对应的 `ProtocolExecutor`

### 任务 2.2 重构现有轮询协议进新接口
把 Phase 0 抽出的 `NegotiationOrchestrator` 逻辑迁成 `IterativeBargainExecutor`。行为保持一致，只是换了架子。

### 任务 2.3 实现 Sealed-Bid 协议
状态机：`open → collecting → revealed → settled`。
- Orchestrator 基于 `AgentTransport.callAgent()` 的 `CompletableFuture` 能力，**并发**向 Buyer 和 Seller 发送出价请求
- 收到两份报价后，按规则开标（例如：买方出价 ≥ 卖方报价则成交于中间价，否则流拍）
- 信息隔离天然满足：Buyer/Seller 是独立 LLM Agent，各自 context 隔离。Orchestrator 只需保证构建双方 context 时不把对方本轮出价塞进去即可，无需额外机制

### 任务 2.4 实现 One-Shot 协议（垄断定价对照组）
状态机：`proposed → accepted | rejected`。
- 卖方按自己的策略出一个固定报价（take-it-or-leave-it）
- 买方只能调用 `acceptDeal` 或 `rejectDeal`，不再调用 `makeOffer`
- **定位明确**：这是"垄断定价权"下的成交率对照组，不是和 Iterative / Sealed-Bid 对等的"第三种谈判方式"。它测的是"一方完全定价 + 另一方只能接受/拒绝"场景下，不同策略的成交率和单方满意度
- 作为 baseline 的价值：和 Iterative 对比能量化"多轮讨价还价给买方带来了多少福利"

### 任务 2.5 跨协议实验

**实验矩阵**（前置确定，不再到 Phase 末尾再纠结）：
- **协议**：Iterative / Sealed-Bid / One-Shot
- **策略组合**：4 种（2 买 × 2 卖，沿用现有 AGGRESSIVE/CONSERVATIVE × PREMIUM/FLEXIBLE）
- **商品**：3 个，覆盖低/中/高价位（沿用 svc-001 / svc-003 / svc-007）
- **RAG 维度**：只在 Iterative 下跑全 4 种模式（RAG 对单次出价的 Sealed-Bid / One-Shot 影响有限），Sealed-Bid 和 One-Shot 只跑 NONE 和 BOTH 两种
- **每组 trials**：5 次


**指标**：
- 成交率
- 买方 / 卖方满意度（**满意度公式需先修，不能让社会福利恒等于 1.2**，见下方备注）
- 社会福利（推荐用 Nash 乘积 `buyer × seller`，对分配均衡敏感）
- 平均谈判时长（轮数 + wall clock 时间）

**统计方法**：对每组关键指标跑 bootstrap 1000 次算 95% CI，报告里带误差棒，避免重蹈现有实验报告"5% 差异就下结论"的覆辙。

**备注：修满意度公式**
现有公式让社会福利 = `(1 - ratio + 0.2) + ratio = 1.2` 是恒等式，不是真实指标。需要在 Phase 2 前或 Phase 2 中改为非线性互补公式，否则所有关于"社会福利"的结论都没意义。推荐改成 Nash 乘积，或基于"买方 vs 自己上限、卖方 vs 自己底线的剩余比例"。

**完成标志**：产出三协议对比表 + Nash 社会福利 CI 图，能讲清"什么场景用什么协议"。

---

## Phase 3 — PolicyEngine 轻量重构

目的：把现在散落在 `BuyerTools.violations` 和 Orchestrator 收敛判断里的异常检测逻辑，收敛成独立组件。不扩展异常种类，只做重构，突出"可扩展的异常检测组件"这一设计能力。

### 任务 3.1 抽出 PolicyEngine 组件
独立模块，由各 `ProtocolExecutor` 在每次状态变化后调用（轮询协议每轮调一次，sealed-bid 在开标后调，one-shot 在决策后调）。输入当前谈判上下文，输出 `List<AnomalyRecord>`。

### 任务 3.2 收编现有 3 类异常规则
把现有逻辑改造成独立的 `PolicyRule` 实现：
- `PriceBoundaryRule`：价格越界（迁移现有 OVERPAYMENT 逻辑）
- `DeadlockRule`：达到最大轮数未收敛（迁移现有 DEADLOCK 逻辑）
- `ConstraintViolationRule`：Tools 层的约束违规（迁移 `BuyerTools/SellerTools.violations`）

后续如需扩展新异常类型，只需新增 `PolicyRule` 实现，无需改动 Executor。

### 任务 3.3 单元测试
每条规则写独立单测，造正例反例各 3-5 条，验证规则逻辑正确。这是简历上讲"可测试可扩展"的底气。

---

## 产出清单

- [ ] 分布式 3 服务系统（Orchestrator + Buyer + Seller），`docker compose up` 一键启动
- [ ] A2A 协议（Agent Card + tasks/send + SSE）
- [ ] 3 种谈判协议（Iterative / Sealed-Bid / One-Shot）+ 可扩展的 `ProtocolExecutor` 抽象
- [ ] PolicyEngine 组件 + 3 条规则 + 单元测试
- [ ] Jaeger 跨服务全链路 trace 截图
- [ ] 跨协议实验对比表（成交率、满意度、社会福利）
- [ ] RAG 对满意度影响的原有实验结论
- [ ] README.md + 架构图
- [ ] 简历 bullet 4-5 条
