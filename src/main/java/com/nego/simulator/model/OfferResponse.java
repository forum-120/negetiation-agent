package com.nego.simulator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AgentTransport 每次调用的结构化返回值。
 *
 * <p>将原来散落在 Transport 各字段（lastOffer、accepted）的状态
 * 收拢成一个不可变的结果对象，配合 CompletableFuture 使用。</p>
 *
 * <p>这样 Phase 2 sealed-bid 协议可以并发调用 Buyer/Seller，
 * 用 CompletableFuture.allOf() 等待双方报价，无需改接口。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfferResponse {

    /** 远端 agent 实际使用的 sessionId，可为空。 */
    private String sessionId;

    /** LLM 回复的原始文本 */
    private String text;

    /** 本轮报价金额（由 Tools 层写入，防作弊保证） */
    private double lastOffer;

    /** 是否已接受对方报价（达成成交） */
    private boolean accepted;

    /** 本次调用中 Tools 层检测到的约束违规（可为空列表） */
    private List<AnomalyRecord> violations;
}
