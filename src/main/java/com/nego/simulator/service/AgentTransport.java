package com.nego.simulator.service;

import com.nego.simulator.model.AnomalyRecord;
import com.nego.simulator.model.OfferResponse;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * AgentTransport — Phase 0.2 核心抽象接口。
 *
 * <p>封装"向对方 Agent 发消息并拿回报价"的全部交互。</p>
 *
 * <ul>
 *   <li>当前实现：{@link InProcessTransport}（JVM 内存直调）</li>
 *   <li>Phase 1 实现：A2aHttpTransport（HTTP + Google A2A 协议）</li>
 * </ul>
 *
 * <p><b>为什么返回 CompletableFuture？</b><br>
 * Phase 2 sealed-bid 协议需要同时向 Buyer 和 Seller 发报价请求，
 * 用 {@code CompletableFuture.allOf(sendToBuyer(...), sendToSeller(...))}
 * 可以直接并发调用，无需再改接口。
 * InProcessTransport 在同步场景下直接返回
 * {@code CompletableFuture.completedFuture(result)} 即可。</p>
 *
 * <p>Orchestrator 从此不关心对方是同进程还是跨网络，也不关心是串行还是并行。</p>
 */
public interface AgentTransport extends AutoCloseable {

    /**
     * 向买方发送本轮谈判上下文，异步返回报价结果。
     *
     * @param context 当前轮次的谈判状态（含策略参数、历史报价、RAG 建议等）
     * @return 包含文本回复、报价金额、是否接受、违规记录的 Future
     */
    CompletableFuture<OfferResponse> sendToBuyer(String context);

    /**
     * 向卖方发送本轮谈判上下文，异步返回报价结果。
     */
    CompletableFuture<OfferResponse> sendToSeller(String context);

    /**
     * 在调用 sendToBuyer 前，告知买方对手（卖方）的最新报价，
     * 供 Tools 层 acceptDeal 防作弊绑定使用。
     */
    void setOpponentOfferForBuyer(double sellerPrice);

    /**
     * 在调用 sendToSeller 前，告知卖方对手（买方）的最新报价。
     */
    void setOpponentOfferForSeller(double buyerPrice);

    /** 本场谈判买方累计的约束违规记录。 */
    List<AnomalyRecord> getBuyerViolations();

    /** 本场谈判卖方累计的约束违规记录。 */
    List<AnomalyRecord> getSellerViolations();

    @Override
    void close();
}
