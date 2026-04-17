package com.nego.simulator.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.nego.simulator.model.*;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "nego.role", havingValue = "orchestrator", matchIfMissing = true)
public class NegotiationOrchestrator {

    private final StrategyKnowledgeService strategyKnowledgeService;
    private final Tracer tracer;

    public NegotiationOrchestrator(StrategyKnowledgeService strategyKnowledgeService,
                                   Tracer tracer) {
        this.strategyKnowledgeService = strategyKnowledgeService;
        this.tracer = tracer;
    }

    /**
     * 执行一场完整的多轮谈判。
     *
     * <p>每轮流程：
     * <ol>
     *   <li>买方回合：设置对手报价 → RAG（可选）→ 调 transport.sendToBuyer() → 判断接受</li>
     *   <li>卖方回合：镜像执行</li>
     *   <li>收敛判断：价差比例 ≤ threshold → 取中间价成交</li>
     * </ol>
     * </p>
     *
     * <p>{@link AgentTransport#sendToBuyer} 返回 {@link java.util.concurrent.CompletableFuture}，
     * 当前用 {@code .join()} 同步等待。Phase 2 sealed-bid 可改为 {@code allOf} 并发。</p>
     */
    public NegotiationResult run(ServiceInfo service, NegotiationConfig config,
                                  AgentTransport transport,
                                  int maxRounds, double threshold,
                                  List<AnomalyRecord> anomalies) {

        List<NegotiationMessage> messages = new ArrayList<>();
        double buyerPrice = 0;
        double sellerPrice = service.getAskingPrice();

        for (int round = 1; round <= maxRounds; round++) {

            Span roundSpan = tracer.spanBuilder("negotiation.round")
                    .setAttribute("round", round)
                    .startSpan();
            try (Scope roundScope = roundSpan.makeCurrent()) {

                // ── 买方回合 ──
                transport.setOpponentOfferForBuyer(sellerPrice);
                String buyerContext = buildBuyerContext(service, config.getBuyerStrategy(),
                        round, maxRounds, buyerPrice, sellerPrice);

                String buyerRagQuery = null;
                String buyerRagContext = null;
                if (config.getRagMode() == RagMode.BOTH || config.getRagMode() == RagMode.BUYER_ONLY) {
                    String situation = String.format(
                            "作为买方，第%d轮谈判，对方上一轮出价$%.2f，我方上一轮出价$%.2f，商品要价$%.2f",
                            round, sellerPrice, buyerPrice, service.getAskingPrice());
                    Span ragSpan = tracer.spanBuilder("rag.search")
                            .setAttribute("role", "BUYER").setAttribute("round", round).startSpan();
                    try (Scope ragScope = ragSpan.makeCurrent()) {
                        StrategyKnowledgeService.RagResult ragResult =
                                strategyKnowledgeService.searchStrategy(situation);
                        buyerRagQuery = ragResult.rewrittenQuery;
                        buyerRagContext = ragResult.advice;
                    } finally {
                        ragSpan.end();
                    }
                    buyerContext += "\n[相关策略]\n" + buyerRagContext;
                }

                // agent.buyer.call span 覆盖整个 LLM 调用（含网络 + 推理耗时）
                Span buyerCallSpan = tracer.spanBuilder("agent.buyer.call")
                        .setAttribute("round", round).startSpan();
                OfferResponse buyerOffer;
                try (Scope sc = buyerCallSpan.makeCurrent()) {
                    buyerOffer = transport.sendToBuyer(buyerContext).join();
                } finally {
                    buyerCallSpan.end();
                }

                buyerPrice = buyerOffer.getLastOffer();
                roundSpan.setAttribute("buyer.price", buyerPrice);

                messages.add(NegotiationMessage.builder()
                        .role("BUYER").content(buyerOffer.getText()).price(buyerPrice)
                        .round(round).ragQuery(buyerRagQuery).ragContext(buyerRagContext)
                        .timestamp(LocalDateTime.now()).build());

                if (buyerOffer.isAccepted()) {
                    roundSpan.setAttribute("outcome", "buyer.accepted");
                    return buildResult(true, buyerPrice, service, messages, round, config);
                }

                // ── 卖方回合 ──
                transport.setOpponentOfferForSeller(buyerPrice);
                String sellerContext = buildSellerContext(service, config.getSellerStrategy(),
                        round, maxRounds, sellerPrice, buyerPrice);

                String sellerRagQuery = null;
                String sellerRagContext = null;
                if (config.getRagMode() == RagMode.BOTH || config.getRagMode() == RagMode.SELLER_ONLY) {
                    String situation = String.format(
                            "作为卖方，第%d轮谈判，对方上一轮出价$%.2f，我方上一轮报价$%.2f，商品要价$%.2f",
                            round, buyerPrice, sellerPrice, service.getAskingPrice());
                    Span ragSpan = tracer.spanBuilder("rag.search")
                            .setAttribute("role", "SELLER").setAttribute("round", round).startSpan();
                    try (Scope ragScope = ragSpan.makeCurrent()) {
                        StrategyKnowledgeService.RagResult ragResult =
                                strategyKnowledgeService.searchStrategy(situation);
                        sellerRagQuery = ragResult.rewrittenQuery;
                        sellerRagContext = ragResult.advice;
                    } finally {
                        ragSpan.end();
                    }
                    sellerContext += "\n[相关策略]\n" + sellerRagContext;
                }

                Span sellerCallSpan = tracer.spanBuilder("agent.seller.call")
                        .setAttribute("round", round).startSpan();
                OfferResponse sellerOffer;
                try (Scope sc = sellerCallSpan.makeCurrent()) {
                    sellerOffer = transport.sendToSeller(sellerContext).join();
                } finally {
                    sellerCallSpan.end();
                }

                sellerPrice = sellerOffer.getLastOffer();
                roundSpan.setAttribute("seller.price", sellerPrice);

                messages.add(NegotiationMessage.builder()
                        .role("SELLER").content(sellerOffer.getText()).price(sellerPrice)
                        .round(round).ragQuery(sellerRagQuery).ragContext(sellerRagContext)
                        .timestamp(LocalDateTime.now()).build());

                if (sellerOffer.isAccepted()) {
                    roundSpan.setAttribute("outcome", "seller.accepted");
                    return buildResult(true, sellerPrice, service, messages, round, config);
                }

                // ── 收敛判断 ──
                if (isConverged(buyerPrice, sellerPrice, threshold)) {
                    double midPrice = (buyerPrice + sellerPrice) / 2;
                    double buyerCeiling = service.getAskingPrice() * config.getBuyerStrategy().getCeiling();
                    double sellerFloor  = service.getAskingPrice() * config.getSellerStrategy().getFloor();
                    if (midPrice > buyerCeiling) {
                        anomalies.add(AnomalyRecord.builder().type(AnomalyType.OVERPAYMENT)
                                .description("收敛中间价 $" + midPrice + " 超过买方预算上限 $" + buyerCeiling)
                                .round(round).relevantPrice(midPrice).build());
                    }
                    if (midPrice < sellerFloor) {
                        anomalies.add(AnomalyRecord.builder().type(AnomalyType.OVERPAYMENT)
                                .description("收敛中间价 $" + midPrice + " 低于卖方底线 $" + sellerFloor)
                                .round(round).relevantPrice(midPrice).build());
                    }
                    roundSpan.setAttribute("outcome", "converged");
                    return buildResult(true, midPrice, service, messages, round, config);
                }

            } finally {
                roundSpan.end();
            }
        }

        // 达到最大轮数，判定为 DEADLOCK
        anomalies.add(AnomalyRecord.builder().type(AnomalyType.DEADLOCK)
                .description("达到最大轮数未能收敛，谈判陷入僵局")
                .round(maxRounds).build());
        return buildResult(false, 0, service, messages, maxRounds, config);
    }

    // ────────────────── 辅助方法 ──────────────────

    private String buildBuyerContext(ServiceInfo service, BuyerStrategy strategy,
            int round, int maxRounds, double myLastOffer, double opponentLastOffer) {
        return String.format("""
                [谈判状态]
                商品：%s
                商品描述：%s
                原始要价：$%.2f
                当前轮次：第 %d 轮 / 共 %d 轮
                你的策略：%s（首轮%.0f%%，每轮加%.0f%%-%.0f%%，预算上限%.0f%%）
                你上一轮出价：%s
                对方上一轮出价：%s
                请做出你的下一步谈判动作。
                """,
                service.getName(), service.getDescription(), service.getAskingPrice(),
                round, maxRounds, strategy.getDescription(),
                strategy.getOpenRatio() * 100, strategy.getMinStep() * 100,
                strategy.getMaxStep() * 100, strategy.getCeiling() * 100,
                myLastOffer > 0 ? String.format("$%.2f", myLastOffer) : "尚未出价",
                opponentLastOffer > 0 ? String.format("$%.2f", opponentLastOffer) : "尚未出价");
    }

    private String buildSellerContext(ServiceInfo service, SellerStrategy strategy,
            int round, int maxRounds, double myLastOffer, double opponentLastOffer) {
        return String.format("""
                [谈判状态]
                商品：%s
                商品描述：%s
                原始要价：$%.2f
                当前轮次：第 %d 轮 / 共 %d 轮
                你的策略：%s（首轮%.0f%%，每轮降%.0f%%-%.0f%%，底线%.0f%%）
                你上一轮报价：%s
                对方上一轮出价：%s
                请做出你的下一步谈判动作。
                """,
                service.getName(), service.getDescription(), service.getAskingPrice(),
                round, maxRounds, strategy.getDescription(),
                strategy.getOpenRatio() * 100, strategy.getMinStep() * 100,
                strategy.getMaxStep() * 100, strategy.getFloor() * 100,
                myLastOffer > 0 ? String.format("$%.2f", myLastOffer) : "尚未报价",
                opponentLastOffer > 0 ? String.format("$%.2f", opponentLastOffer) : "尚未出价");
    }

    private boolean isConverged(double buyerPrice, double sellerPrice, double threshold) {
        if (buyerPrice <= 0 || sellerPrice <= 0) return false;
        return Math.abs(sellerPrice - buyerPrice) / sellerPrice <= threshold;
    }

    private NegotiationResult buildResult(boolean agreed, double finalPrice,
            ServiceInfo service, List<NegotiationMessage> messages,
            int rounds, NegotiationConfig config) {
        double originalPrice = service.getAskingPrice();
        double discount = agreed ? (originalPrice - finalPrice) / originalPrice : 0;
        return NegotiationResult.builder()
                .agreed(agreed)
                .finalPrice(agreed ? finalPrice : null)
                .originalPrice(originalPrice)
                .discount(agreed ? discount : null)
                .rounds(rounds)
                .messages(messages)
                .buyerSatisfaction(agreed ? calculateSatisfaction(finalPrice, originalPrice, true) : null)
                .sellerSatisfaction(agreed ? calculateSatisfaction(finalPrice, originalPrice, false) : null)
                .buyerStrategy(config.getBuyerStrategy().name())
                .sellerStrategy(config.getSellerStrategy().name())
                .ragMode(config.getRagMode() != null ? config.getRagMode().name() : "NONE")
                .serviceId(service.getId())
                .build();
    }

    private double calculateSatisfaction(double finalPrice, double originalPrice, boolean isBuyer) {
        double ratio = finalPrice / originalPrice;
        return isBuyer ? Math.max(0, Math.min(1, 1 - ratio + 0.2)) : Math.max(0, Math.min(1, ratio));
    }
}
