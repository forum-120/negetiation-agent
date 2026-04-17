package com.nego.simulator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 谈判消息领域模型。
 *
 * <p>该类用于表示谈判过程中的一条消息记录，是系统中最基础的对话数据单元。</p>
 *
 * <p>它既服务于前端聊天记录展示，也服务于后端谈判引擎的流程编排。
 * 通过这个对象，系统可以知道：</p>
 * <ul>
 *     <li>这条消息是谁发送的</li>
 *     <li>消息文本内容是什么</li>
 *     <li>当前轮次关联的报价是多少</li>
 *     <li>消息产生的时间是什么时候</li>
 * </ul>
 *
 * <p>在后续阶段中，谈判引擎会维护一个消息列表，
 * 并将多个 {@code NegotiationMessage} 组合成完整的谈判历史。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NegotiationMessage {

    /**
     * 消息发送方角色。
     *
     * <p>用于标识当前消息来自哪一方，常见取值例如：</p>
     * <ul>
     *     <li>{@code BUYER}：买方</li>
     *     <li>{@code SELLER}：卖方</li>
     *     <li>{@code SYSTEM}：系统</li>
     * </ul>
     *
     * <p>后续前端可以基于该字段决定消息显示位置，
     * 后端也可以基于该字段判断当前对话上下文。</p>
     */
    private String role;

    /**
     * 消息的自然语言内容。
     *
     * <p>这是用户在界面上能直接看到的谈判文本，
     * 例如“我愿意出价 700 元”或“这个价格太低了，我最低接受 900 元”。</p>
     *
     * <p>该字段主要用于：</p>
     * <ul>
     *     <li>聊天记录展示</li>
     *     <li>历史追踪</li>
     *     <li>为后续 Agent 提供上下文</li>
     * </ul>
     */
    private String content;

    /**
     * 该条消息关联的报价。
     *
     * <p>谈判系统不应只依赖自然语言文本来解析价格，
     * 因此需要单独用结构化字段保存报价信息。</p>
     *
     * <p>该字段主要用于：</p>
     * <ul>
     *     <li>买卖双方报价比较</li>
     *     <li>收敛判断</li>
     *     <li>折扣和最终结果计算</li>
     * </ul>
     *
     * <p>如果某条消息只是系统提示、欢迎语或纯文本信息，
     * 该字段可以为空。</p>
     */
    private Double price;

    /**
     * 当前消息所属的谈判轮次。
     *
     * <p>该字段用于标识这条消息出现在第几轮谈判中，
     * 便于在前端展示“第 1 轮 / 第 2 轮”，
     * 也便于后端做日志记录和问题排查。</p>
     *
     * <p>虽然最小实现中不一定强依赖该字段，
     * 但它对系统可观察性和后续统计分析很有帮助。</p>
     */
    private Integer round;

    /**
     * 消息创建时间。
     *
     * <p>用于记录该条消息产生的时间点，便于：</p>
     * <ul>
     *     <li>按时间排序消息</li>
     *     <li>追踪完整谈判过程</li>
     *     <li>为审计和日志分析提供依据</li>
     * </ul>
     */
    private LocalDateTime timestamp;

    /**
     * RAG 对话改写结果 (Query Rewrite)。
     * 记录供后续分析使用的 DeepSeek 改写状态。
     */
    private String ragQuery;

    /**
     * RAG 返回的核心策略内容。
     * 记录这轮系统到底喂了什么策略给 Agent。
     */
    private String ragContext;

}
