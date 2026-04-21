package com.nego.simulator.protocol;

import com.nego.simulator.model.*;
import com.nego.simulator.policy.PolicyContext;
import com.nego.simulator.policy.PolicyEngine;
import com.nego.simulator.service.AgentTransport;
import com.nego.simulator.service.StrategyKnowledgeService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@SupportsProtocol(NegotiateProtocol.ITERATIVE_BARGAIN)
public class IterativeBargainExecutor implements ProtocolExecutor {

    private final StrategyKnowledgeService strategyKnowledgeService;
    private final Tracer tracer;
    private final PolicyEngine policyEngine;

    public IterativeBargainExecutor(
            @Autowired(required = false) StrategyKnowledgeService strategyKnowledgeService,
            Tracer tracer,
            PolicyEngine policyEngine) {
        this.strategyKnowledgeService = strategyKnowledgeService;
        this.tracer = tracer;
        this.policyEngine = policyEngine;
    }

    @Override
    public NegotiationResult execute(ServiceInfo service, NegotiationConfig config,
                                     AgentTransport transport, List<AnomalyRecord> anomalies,
                                     String negotiationId, int trialIndex) {

        int maxRounds = config.getMaxRounds() != null ? config.getMaxRounds() : 8;
        double threshold = config.getConvergenceThreshold() != null ? config.getConvergenceThreshold() : 0.03;

        List<NegotiationMessage> messages = new ArrayList<>();
        double buyerPrice = 0;
        double sellerPrice = service.getAskingPrice();

        for (int round = 1; round <= maxRounds; round++) {

            Span roundSpan = tracer.spanBuilder("negotiation.round")
                    .setAttribute("round", round)
                    .startSpan();
            try (Scope roundScope = roundSpan.makeCurrent()) {

                transport.setOpponentOfferForBuyer(sellerPrice);
                String buyerContext = buildBuyerContext(service, config.getBuyerStrategy(),
                        round, maxRounds, buyerPrice, sellerPrice);

                String buyerRagQuery = null;
                String buyerRagContext = null;
                if (strategyKnowledgeService != null && (config.getRagMode() == RagMode.BOTH || config.getRagMode() == RagMode.BUYER_ONLY)) {
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

                Span buyerCallSpan = tracer.spanBuilder("agent.buyer.call")
                        .setAttribute("round", round).startSpan();
                OfferResponse buyerOffer;
                try (Scope sc = buyerCallSpan.makeCurrent()) {
                    try {
                        buyerOffer = transport.sendToBuyer(buyerContext).get(90, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Buyer agent call interrupted", e);
                    } catch (Exception e) {
                        throw new RuntimeException("Buyer agent call failed or timed out", e);
                    }
                } finally {
                    buyerCallSpan.end();
                }

                double previousBuyerPrice = buyerPrice;
                double rawBuyerPrice = buyerOffer.getLastOffer();
                buyerPrice = resolveStandingPrice(previousBuyerPrice, rawBuyerPrice);
                roundSpan.setAttribute("buyer.raw_price", rawBuyerPrice);
                roundSpan.setAttribute("buyer.price", buyerPrice);
                roundSpan.setAttribute("buyer.has_structured_offer", rawBuyerPrice > 0);

                messages.add(NegotiationMessage.builder()
                        .role("BUYER").content(buyerOffer.getText())
                        .price(resolveMessagePrice(previousBuyerPrice, rawBuyerPrice))
                        .round(round).ragQuery(buyerRagQuery).ragContext(buyerRagContext)
                        .timestamp(LocalDateTime.now()).build());

                if (buyerOffer.isAccepted()) {
                    roundSpan.setAttribute("outcome", "buyer.accepted");
                    return buildResult(true, buyerPrice, service, messages, round, config,
                            negotiationId, trialIndex);
                }

                transport.setOpponentOfferForSeller(buyerPrice);
                String sellerContext = buildSellerContext(service, config.getSellerStrategy(),
                        round, maxRounds, sellerPrice, buyerPrice);

                String sellerRagQuery = null;
                String sellerRagContext = null;
                if (strategyKnowledgeService != null && (config.getRagMode() == RagMode.BOTH || config.getRagMode() == RagMode.SELLER_ONLY)) {
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
                    try {
                        sellerOffer = transport.sendToSeller(sellerContext).get(90, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Seller agent call interrupted", e);
                    } catch (Exception e) {
                        throw new RuntimeException("Seller agent call failed or timed out", e);
                    }
                } finally {
                    sellerCallSpan.end();
                }

                double previousSellerPrice = sellerPrice;
                double rawSellerPrice = sellerOffer.getLastOffer();
                sellerPrice = resolveStandingPrice(previousSellerPrice, rawSellerPrice);
                roundSpan.setAttribute("seller.raw_price", rawSellerPrice);
                roundSpan.setAttribute("seller.price", sellerPrice);
                roundSpan.setAttribute("seller.has_structured_offer", rawSellerPrice > 0);

                messages.add(NegotiationMessage.builder()
                        .role("SELLER").content(sellerOffer.getText())
                        .price(resolveMessagePrice(previousSellerPrice, rawSellerPrice))
                        .round(round).ragQuery(sellerRagQuery).ragContext(sellerRagContext)
                        .timestamp(LocalDateTime.now()).build());

                if (sellerOffer.isAccepted()) {
                    roundSpan.setAttribute("outcome", "seller.accepted");
                    return buildResult(true, sellerPrice, service, messages, round, config,
                            negotiationId, trialIndex);
                }

                if (isConverged(buyerPrice, sellerPrice, threshold)) {
                    double midPrice = (buyerPrice + sellerPrice) / 2;
                    PolicyContext ctx = PolicyContext.builder()
                            .originalPrice(service.getAskingPrice())
                            .buyerCeiling(service.getAskingPrice() * config.getBuyerStrategy().getCeiling())
                            .sellerFloor(service.getAskingPrice() * config.getSellerStrategy().getFloor())
                            .buyerOffer(buyerPrice).sellerOffer(sellerPrice)
                            .settlementPrice(midPrice)
                            .currentRound(round).maxRounds(maxRounds)
                            .negotiationId(negotiationId)
                            .protocol(NegotiateProtocol.ITERATIVE_BARGAIN)
                            .build();
                    anomalies.addAll(policyEngine.evaluate(ctx));
                    roundSpan.setAttribute("outcome", "converged");
                    return buildResult(true, midPrice, service, messages, round, config,
                            negotiationId, trialIndex);
                }

            } finally {
                roundSpan.end();
            }
        }

        PolicyContext deadlockCtx = PolicyContext.builder()
                .settlementPrice(0)
                .currentRound(maxRounds).maxRounds(maxRounds)
                .buyerOffer(buyerPrice).sellerOffer(sellerPrice)
                .negotiationId(negotiationId)
                .protocol(NegotiateProtocol.ITERATIVE_BARGAIN)
                .buyerCeiling(service.getAskingPrice() * config.getBuyerStrategy().getCeiling())
                .sellerFloor(service.getAskingPrice() * config.getSellerStrategy().getFloor())
                .originalPrice(service.getAskingPrice())
                .build();
        anomalies.addAll(policyEngine.evaluate(deadlockCtx));
        return buildResult(false, 0, service, messages, maxRounds, config,
                negotiationId, trialIndex);
    }

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

    // A reject-without-counteroffer should not erase the previous standing quote.
    private double resolveStandingPrice(double previousPrice, double rawOfferPrice) {
        return rawOfferPrice > 0 ? rawOfferPrice : previousPrice;
    }

    private Double resolveMessagePrice(double previousPrice, double rawOfferPrice) {
        double effectivePrice = resolveStandingPrice(previousPrice, rawOfferPrice);
        return effectivePrice > 0 ? effectivePrice : null;
    }

    private NegotiationResult buildResult(boolean agreed, double finalPrice,
            ServiceInfo service, List<NegotiationMessage> messages,
            int rounds, NegotiationConfig config,
            String negotiationId, int trialIndex) {
        double originalPrice = service.getAskingPrice();
        double discount = agreed ? (originalPrice - finalPrice) / originalPrice : 0;

        Double buyerSat = null, sellerSat = null, nashWelfare = null;
        if (agreed) {
            double ceiling = originalPrice * config.getBuyerStrategy().getCeiling();
            double floor = originalPrice * config.getSellerStrategy().getFloor();
            buyerSat = Math.max(0, (ceiling - finalPrice) / ceiling);
            sellerSat = Math.max(0, (finalPrice - floor) / (originalPrice - floor));
            nashWelfare = buyerSat * sellerSat;
        }

        return NegotiationResult.builder()
                .agreed(agreed)
                .finalPrice(agreed ? finalPrice : null)
                .originalPrice(originalPrice)
                .discount(agreed ? discount : null)
                .rounds(rounds)
                .messages(messages)
                .buyerSatisfaction(buyerSat)
                .sellerSatisfaction(sellerSat)
                .nashWelfare(nashWelfare)
                .buyerStrategy(config.getBuyerStrategy().name())
                .sellerStrategy(config.getSellerStrategy().name())
                .ragMode(config.getRagMode() != null ? config.getRagMode().name() : "NONE")
                .serviceId(service.getId())
                .protocol(config.getProtocol() != null ? config.getProtocol().name() : "ITERATIVE_BARGAIN")
                .negotiationId(negotiationId)
                .trialIndex(trialIndex > 0 ? trialIndex : null)
                .maxRounds(config.getMaxRounds())
                .convergenceThreshold(config.getConvergenceThreshold())
                .startedAt(OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME))
                .build();
    }
}
