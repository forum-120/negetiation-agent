package com.nego.simulator.service;

import com.nego.simulator.model.AnomalyRecord;

import java.util.List;

/**
 * AgentTransport — Phase 0.2 核心抽象接口。
 *
 * <p>
 * 封装"向对方 Agent 发消息并拿回报价"的全部交互。
 * </p>
 *
 * <ul>
 *   <li>当前实现：{@link InProcessTransport}（JVM 内存直调，与老版行为完全一致）</li>
 *   <li>Phase 1 实现：{@code A2aHttpTransport}（HTTP + Google A2A 协议）</li>
 * </ul>
 *
 * <p>Orchestrator 只依赖此接口，不关心对方是同进程还是跨网络。</p>
 */
public interface AgentTransport extends AutoCloseable {

    String sendToBuyer(String context);

    String sendToSeller(String context);

    boolean isBuyerAccepted();

    boolean isSellerAccepted();

    double getBuyerLastOffer();

    double getSellerLastOffer();

    void setOpponentOfferForBuyer(double sellerPrice);

    void setOpponentOfferForSeller(double buyerPrice);

    List<AnomalyRecord> getBuyerViolations();

    List<AnomalyRecord> getSellerViolations();

    @Override
    void close();
}
