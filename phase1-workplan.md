# Phase 1 工作计划 — 官方 A2A Java SDK 集成

## 技术选型确认

使用 **官方 A2A Java SDK**（`io.github.a2asdk`，原 `a2aproject/a2a-java`），**不用** `spring-ai-community/spring-ai-a2a`。

### SDK 模块结构（关键的三个）

| 模块 | 作用 |
|------|------|
| `a2a-java-sdk-client` | A2A 客户端，含 `Client`、`A2ACardResolver`、`JSONRPCTransport` |
| `a2a-java-sdk-server-common` | 服务端核心：`AgentExecutor`、`AgentEmitter`、`RequestContext`、`AgentCard builder`、所有 spec POJO |
| `a2a-java-sdk-spec` | 纯数据模型（`Task`、`Message`、`TextPart`、`TaskState` 等），由上面两个自动引入 |

> [!IMPORTANT]
> **服务端没有 Spring Boot 专属集成**。官方参考实现基于 **Quarkus**；Spring Boot 侧需要自己写 `@RestController` 把请求路由到 SDK 的 `AgentExecutor`。这正好和你现有的 Controller 层对齐，改造量最小。

---

## Maven 依赖变更

```xml
<properties>
    <!-- 在 <properties> 里新增 -->
    <a2a.sdk.version>0.3.2.Final</a2a.sdk.version>
</properties>

<dependencies>
    <!-- ─── A2A SDK：Buyer / Seller 服务端侧 ─── -->
    <dependency>
        <groupId>io.github.a2asdk</groupId>
        <artifactId>a2a-java-sdk-server-common</artifactId>
        <version>${a2a.sdk.version}</version>
    </dependency>

    <!-- ─── A2A SDK：Orchestrator 客户端侧 ─── -->
    <dependency>
        <groupId>io.github.a2asdk</groupId>
        <artifactId>a2a-java-sdk-client</artifactId>
        <version>${a2a.sdk.version}</version>
    </dependency>
    <!-- client 默认内置 JSONRPC transport，不需要额外加 -->
</dependencies>
```

> [!NOTE]
> `a2a-java-sdk-client` 已内含 `a2a-java-sdk-spec`（数据模型 POJO），`server-common` 也已传递依赖 spec，所以你不需要单独声明 spec 依赖。两个加起来即完整覆盖服务端 + 客户端。

---

## 任务 1.1 — Buyer / Seller 暴露 A2A 接口

### 核心思路：用 SDK 的 `AgentExecutor` + 自己写 Spring MVC Controller

官方 SDK 设计为框架无关：你只需实现 `AgentExecutor` 接口，然后在 Spring `@RestController` 里接收 HTTP 请求、调用 SDK 的处理链路、返回响应。

### 1.1.1 实现 AgentExecutor

**[NEW]** `src/main/java/com/nego/simulator/a2a/BuyerAgentExecutor.java`

```java
package com.nego.simulator.a2a;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nego.simulator.model.OfferResponse;
import com.nego.simulator.service.BuyerAgentService;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.*;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "nego.role", havingValue = "buyer")
public class BuyerAgentExecutor implements AgentExecutor {

    private final BuyerAgentService buyerService;
    private final ObjectMapper objectMapper;

    public BuyerAgentExecutor(BuyerAgentService buyerService, ObjectMapper objectMapper) {
        this.buyerService = buyerService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void execute(RequestContext ctx, AgentEmitter emitter) throws JSONRPCError {
        if (ctx.getTask() == null) emitter.submit();
        emitter.startWork();

        // 1. 提取 context 文本
        String context = extractText(ctx.getMessage());

        // 2. 从 metadata 读取 sessionId 和 opponentOffer
        Map<String, Object> meta = ctx.getMessage().metadata();
        String sessionId = (String) meta.getOrDefault("sessionId", "default");
        double opponentOffer = ((Number) meta.getOrDefault("opponentOffer", 0.0)).doubleValue();

        // 3. 调用业务逻辑
        OfferResponse offer = buyerService.respond(sessionId, context, opponentOffer);

        // 4. 将 OfferResponse 序列化为 JSON，放进 TextPart artifact
        String offerJson = objectMapper.writeValueAsString(offer);  // try-catch 省略
        emitter.addArtifact(List.of(new TextPart(offerJson)));
        emitter.complete();
    }

    @Override
    public void cancel(RequestContext ctx, AgentEmitter emitter) throws JSONRPCError {
        emitter.cancel();
    }

    private String extractText(Message message) {
        return message.parts().stream()
            .filter(p -> p instanceof TextPart)
            .map(p -> ((TextPart) p).text())
            .findFirst().orElse("");
    }
}
```

> `SellerAgentExecutor` 与此完全对称，Profile 改为 `seller`，注入 `SellerAgentService`。

**[NEW]** `src/main/java/com/nego/simulator/a2a/SellerAgentExecutor.java` — 与上对称

---

### 1.1.2 注册 AgentCard Bean

**[NEW]** `src/main/java/com/nego/simulator/a2a/AgentCardConfig.java`

```java
@Configuration
public class AgentCardConfig {

    @Bean
    @ConditionalOnProperty(name = "nego.role", havingValue = "buyer")
    public AgentCard buyerAgentCard(@Value("${server.port:8081}") int port) {
        return AgentCard.builder()
            .name("NegotiationBuyerAgent")
            .description("LLM-powered buyer agent for iterative price negotiation")
            .supportedInterfaces(List.of(
                new AgentInterface(TransportProtocol.JSONRPC.asString(),
                    "http://buyer-agent:" + port + "/a2a")))
            .version("1.0.0")
            .capabilities(AgentCapabilities.builder().streaming(false).build())
            .defaultInputModes(List.of("text"))
            .defaultOutputModes(List.of("text"))
            .skills(List.of(AgentSkill.builder()
                .id("negotiate_price")
                .name("Price Negotiation")
                .description("Makes counter-offers using buyer strategy and optional RAG context")
                .tags(List.of("negotiation", "pricing"))
                .build()))
            .build();
    }

    @Bean
    @ConditionalOnProperty(name = "nego.role", havingValue = "seller")
    public AgentCard sellerAgentCard(@Value("${server.port:8082}") int port) {
        // 对称实现，name = "NegotiationSellerAgent"
        ...
    }

    @Bean
    @ConditionalOnProperty(name = "nego.role", havingValue = "orchestrator", matchIfMissing = true)
    public AgentCard orchestratorAgentCard(@Value("${server.port:8080}") int port) {
        // Orchestrator 自描述
        ...
    }
}
```

---

### 1.1.3 用 Spring MVC Controller 桥接 SDK

**[MODIFY]** `src/main/java/com/nego/simulator/controller/BuyerAgentController.java`

```java
@RestController
@RequestMapping("/a2a")
@ConditionalOnProperty(name = "nego.role", havingValue = "buyer")
public class BuyerAgentController {

    private final BuyerAgentExecutor executor;
    private final AgentCard agentCard;

    // ① Agent 发现端点
    @GetMapping("/card")
    public AgentCard getCard() {
        return agentCard;
    }

    // ② A2A sendMessage（JSON-RPC 2.0）
    @PostMapping
    public ResponseEntity<?> sendMessage(@RequestBody JsonNode body) {
        // 用 SDK 的请求处理路径（blocking 模式）：
        // 1. 从 body 解析 Message
        // 2. 创建 RequestContext
        // 3. 调用 executor.execute()
        // 4. 从 EventQueue 收集 Task 结果
        // 5. 封装成 JSON-RPC 响应返回
        return ResponseEntity.ok(buildJsonRpcResponse(body, processRequest(body)));
    }
}
```

> [!NOTE]
> SDK 的 `server-common` 提供了 `BlockingRequestHandler` 工具类，可以用来同步等待 `AgentEmitter` 完成。具体 API 需在引入依赖后查看 Javadoc 确认名称。如果没有现成的 blocking wrapper，自行用 `CompletableFuture + EventQueue` 实现五行代码即可。

**原有 `/agent/buyer/*` 端点**：在 `BuyerAgentController` 里保留（换一个新 Controller 类 `BuyerLegacyController` 也可以），供本地单测场景回退使用。

---

## 任务 1.2 — Orchestrator 改造成 A2A 客户端

### 1.2.1 改写 HttpTransport → A2AHttpTransport

**[MODIFY]** `src/main/java/com/nego/simulator/service/HttpTransport.java`

原来用 `RestTemplate` 调自定义端点，改为用 SDK 的 `Client` 调 A2A 端点。

**构造阶段**（原来做 `/agent/buyer/init`，现在做服务发现）：

```java
// Orchestrator 启动时，用 A2ACardResolver 拉取 AgentCard 验证存活
A2ACardResolver resolver = new A2ACardResolver(buyerBaseUrl);
AgentCard buyerCard = resolver.getAgentCard();   // GET /a2a/card

// 构建客户端
this.buyerClient = Client.builder(buyerCard)
    .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
    .build();
// selllerClient 对称
```

**sendToBuyer 阶段**（原来 POST /agent/buyer/call，现在 A2A sendMessage）：

```java
@Override
public CompletableFuture<OfferResponse> sendToBuyer(String context) {
    // 把 context + sessionId + opponentOffer 打包成 A2A Message
    Message msg = new Message.Builder()
        .role(Message.Role.USER)
        .parts(List.of(new TextPart(context)))
        .metadata(Map.of(
            "sessionId",     buyerSessionId,
            "opponentOffer", opponentOfferForBuyer
        ))
        .build();

    CompletableFuture<OfferResponse> future = new CompletableFuture<>();

    buyerClient.sendMessage(msg,
        List.of((event, card) -> {
            if (event instanceof TaskEvent te) {
                Task task = te.getTask();
                if (task.getStatus().state() == TaskState.COMPLETED) {
                    // 从 artifacts 解析 OfferResponse JSON
                    String json = extractArtifactText(task);
                    future.complete(objectMapper.readValue(json, OfferResponse.class));
                }
            }
        }),
        err -> future.completeExceptionally(err)
    );
    return future;
}
```

> `sendToSeller` 完全对称。`opponentOfferForBuyer` / `opponentOfferForSeller` 字段保留原逻辑不变。

**关闭阶段**（原来 DELETE session，现在无状态，直接 no-op）：

```java
@Override
public void close() {
    // A2A 无状态，无需显式销毁 session
    // buyerSessionId 由 Orchestrator 自生成 UUID，无需通知 Buyer/Seller
}
```

---

### 1.2.2 sessionId 管理方案

| 阶段 | 原 HttpTransport | 新 A2aHttpTransport |
|------|-----------------|---------------------|
| 初始化 | POST `/agent/buyer/init` → 拿 sessionId | Orchestrator 自生成 UUID，存为字段 |
| 每次调用 | request body 带 sessionId | `Message.metadata["sessionId"]` |
| Buyer/Seller 侧 | 从 request body 读 sessionId | 从 `RequestContext.getMessage().metadata()` 读 |
| 销毁 | DELETE `/agent/buyer/session/{id}` | 无需操作（A2A 无状态 Task 模型） |

`BuyerAgentService` 内部维护 `Map<String, SessionState>`（含 ChatMemory），sessionId 为 key，逻辑不变。

---

### 1.2.3 Orchestrator 暴露自己的 AgentCard

**[MODIFY]** `src/main/java/com/nego/simulator/controller/NegotiationController.java`

新增一个端点（也可放到新 Controller）：

```java
@GetMapping("/a2a/card")
public AgentCard getOrchestratorCard() {
    return agentCard;  // 注入 orchestratorAgentCard Bean
}
```

---

## 任务 1.3 — 多服务 docker-compose 部署

**[NEW]** `docker-compose.yml`（项目根目录）

```yaml
version: '3.9'
services:

  buyer-agent:
    build: .
    ports: ["8081:8081"]
    environment:
      SPRING_PROFILES_ACTIVE: buyer
      SERVER_PORT: "8081"
      DASHSCOPE_API_KEY: ${DASHSCOPE_API_KEY}
      OTEL_EXPORTER_OTLP_ENDPOINT: http://jaeger:4317
    depends_on:
      redis: { condition: service_healthy }
    restart: on-failure

  seller-agent:
    build: .
    ports: ["8082:8082"]
    environment:
      SPRING_PROFILES_ACTIVE: seller
      SERVER_PORT: "8082"
      DASHSCOPE_API_KEY: ${DASHSCOPE_API_KEY}
      OTEL_EXPORTER_OTLP_ENDPOINT: http://jaeger:4317
    depends_on:
      redis: { condition: service_healthy }
    restart: on-failure

  orchestrator:
    build: .
    ports: ["8080:8080"]
    environment:
      SPRING_PROFILES_ACTIVE: orchestrator
      SERVER_PORT: "8080"
      DASHSCOPE_API_KEY: ${DASHSCOPE_API_KEY}
      NEGO_BUYER_URL: http://buyer-agent:8081
      NEGO_SELLER_URL: http://seller-agent:8082
      OTEL_EXPORTER_OTLP_ENDPOINT: http://jaeger:4317
    depends_on:
      buyer-agent: { condition: service_started }
      seller-agent: { condition: service_started }
      redis: { condition: service_healthy }
      jaeger: { condition: service_started }
    restart: on-failure

  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 5

  jaeger:
    image: jaegertracing/all-in-one:1.55
    ports:
      - "16686:16686"   # Jaeger UI
      - "4317:4317"     # OTLP gRPC 接入

  # Milvus 暂时注释（Phase 1 验证时本地起，避免 docker 内存压力）
  # milvus: ...
```

**[NEW]** `Dockerfile`（如尚未有）

```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/negotiation-simulator-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", \
  "-Dotel.java.global-autoconfigure.enabled=true", \
  "-javaagent:/app/opentelemetry-javaagent.jar", \
  "-jar", "app.jar"]
```

> OTel Java Agent 自动拦截 `java.net.http.HttpClient`（SDK 默认的 `JdkA2AHttpClient` 底层），无需手动注入 `traceparent` header。

---

## OTel 跨服务传播确认清单

- [ ] `pom.xml` 中有 `opentelemetry-exporter-otlp` 和 `micrometer-tracing-bridge-otel`（Phase 0.1 应已完成）
- [ ] `application.yml` 各 Profile 都有：
  ```yaml
  management.tracing.sampling.probability: 1.0
  ```
- [ ] `JdkA2AHttpClient` 基于 `java.net.http.HttpClient`，OTel Java Agent 自动注入 W3C `traceparent`
- [ ] Buyer/Seller 服务收到请求时，Spring Boot + OTel 自动从 incoming header 恢复 trace context

---

## 里程碑验证步骤

```bash
# 打包
mvn clean package -DskipTests

# 启动
docker compose up --build

# 等 30s 后，发起一次谈判
curl -X POST http://localhost:8080/api/negotiate \
  -H 'Content-Type: application/json' \
  -d '{"serviceId":"svc-001","buyerStrategy":"AGGRESSIVE","sellerStrategy":"FLEXIBLE","ragMode":"NONE"}'

# 验证 A2A card 发现
curl http://localhost:8081/a2a/card   # buyer AgentCard
curl http://localhost:8082/a2a/card   # seller AgentCard
curl http://localhost:8080/a2a/card   # orchestrator AgentCard

# 打开 Jaeger 验证跨服务 trace
open http://localhost:16686
# 查找 service = "orchestrator"，看 trace 里是否有 buyer-agent / seller-agent 的 span
```

---

## 执行顺序与时间估算

| # | 子任务 | 文件 | 工时 |
|---|--------|------|------|
| 1 | 添加 SDK 依赖，验证 `mvn dependency:resolve` 通过 | `pom.xml` | 0.5h |
| 2 | 查看 SDK Javadoc，确认 `AgentEmitter`、`RequestContext`、`BlockingRequestHandler` 实际 API | — | 0.5h |
| 3 | 实现 `BuyerAgentExecutor`（含 sessionId 提取 + OfferResponse 序列化） | 新建 `a2a/BuyerAgentExecutor.java` | 1h |
| 4 | 实现 `SellerAgentExecutor`（对称） | 新建 `a2a/SellerAgentExecutor.java` | 0.5h |
| 5 | 实现 `AgentCardConfig`（三个 Profile 的 AgentCard Bean） | 新建 `a2a/AgentCardConfig.java` | 0.5h |
| 6 | 改造 `BuyerAgentController`：保留旧端点，新增 `GET /a2a/card` + `POST /a2a` | `controller/BuyerAgentController.java` | 1h |
| 7 | 改造 `SellerAgentController`（对称） | `controller/SellerAgentController.java` | 0.5h |
| 8 | 改写 `HttpTransport` 为 `A2aHttpTransport`（`Client` + `A2ACardResolver`） | `service/HttpTransport.java` | 1.5h |
| 9 | `NegotiationController` 补 Orchestrator `GET /a2a/card` | `controller/NegotiationController.java` | 0.25h |
| 10 | 编写 `docker-compose.yml` + `Dockerfile` | 新建 | 1h |
| 11 | OTel 跨服务 trace 验证 + Jaeger 截图 | 手动测试 | 1h |
| **合计** | | | **~8h** |

---

## 风险与解决策略

> [!WARNING]
> **风险 1：`AgentEmitter.addArtifact()` 后无法拿到返回值**  
> `AgentEmitter` 是 fire-and-forget 事件推送模型。`A2aHttpTransport` 侧需要用 `CompletableFuture` + consumer callback 等待 `TaskState.COMPLETED` 事件，再从 `Task.artifacts` 解析 OfferResponse。见 1.2.1 中的代码。

> [!WARNING]
> **风险 2：A2ACardResolver 默认调 `/.well-known/agent.json`，但你的端点是 `/a2a/card`**  
> 查看 SDK 源码确认构造函数签名：`new A2ACardResolver(baseUrl)` 会调 `{baseUrl}/a2a/card` 还是 `{baseUrl}/.well-known/agent.json`。如果不匹配，传自定义路径或直接用 `RestTemplate.getForObject` 手动拉卡片。

> [!NOTE]
> **风险 3：`BuyerAgentService` 初始化需要 `strategy` + `askingPrice`，但 A2A 调用不含这些**  
> 解决：`sessionId` 首次出现时，Orchestrator 在 metadata 里额外传 `strategy` 和 `askingPrice`，`BuyerAgentExecutor` 收到后初始化 session；后续轮次检测到 sessionId 已存在则跳过初始化。这等价于原来的 `/agent/buyer/init` 语义，只是迁移到了 A2A metadata 通道。

---

## 完成后简历表述

> "Implemented A2A (Agent-to-Agent) protocol connectivity using the **official A2A Java SDK** (`a2aproject/a2a-java`). Exposed Buyer/Seller agents as A2A servers via `AgentExecutor` + Spring MVC bridge, with `AgentCard` discovery endpoints. Refactored `HttpTransport` to use the SDK's `Client` + `JSONRPCTransport` for standard-compliant agent-to-agent calls. Deployed a 3-service distributed system (Orchestrator:8080, BuyerAgent:8081, SellerAgent:8082) with docker-compose; verified end-to-end distributed traces across service boundaries in Jaeger using W3C `traceparent` propagation."
