package com.nego.simulator.protocol;

import com.nego.simulator.model.*;
import com.nego.simulator.policy.PolicyContext;
import com.nego.simulator.policy.PolicyEngine;
import com.nego.simulator.service.AgentTransport;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
@SupportsProtocol(NegotiateProtocol.SEALED_BID)
public class SealedBidExecutor implements ProtocolExecutor {

    private final Tracer tracer;
    private final PolicyEngine policyEngine;

    public SealedBidExecutor(Tracer tracer, PolicyEngine policyEngine) {
        this.tracer = tracer;
        this.policyEngine = policyEngine;
    }

    @Override
    public NegotiationResult execute(ServiceInfo service, NegotiationConfig config,
                                     AgentTransport transport, List<AnomalyRecord> anomalies,
                                     String negotiationId, int trialIndex) {

        Span sessionSpan = tracer.spanBuilder("sealed-bid.session")
                .setAttribute("protocol", "SEALED_BID").startSpan();
        try (Scope scope = sessionSpan.makeCurrent()) {

            String buyerContext = buildSealedBuyerContext(service, config);
            String sellerContext = buildSealedSellerContext(service, config);

            transport.setOpponentOfferForBuyer(0);
            transport.setOpponentOfferForSeller(0);

            CompletableFuture<OfferResponse> buyerFuture = transport.sendToBuyer(buyerContext);
            CompletableFuture<OfferResponse> sellerFuture = transport.sendToSeller(sellerContext);

            Span bidSpan = tracer.spanBuilder("sealed-bid.collecting").startSpan();
            OfferResponse buyerOffer, sellerOffer;
            try {
                CompletableFuture.allOf(buyerFuture, sellerFuture).get(90, TimeUnit.SECONDS);
                buyerOffer = buyerFuture.join();
                sellerOffer = sellerFuture.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Sealed-bid collecting interrupted", e);
            } catch (Exception e) {
                throw new RuntimeException("Sealed-bid collecting failed or timed out", e);
            } finally {
                bidSpan.end();
            }

            double buyerBid = buyerOffer.getLastOffer();
            double sellerBid = sellerOffer.getLastOffer();

            sessionSpan.setAttribute("buyer.bid", buyerBid);
            sessionSpan.setAttribute("seller.bid", sellerBid);

            List<NegotiationMessage> messages = new ArrayList<>();
            messages.add(NegotiationMessage.builder().role("BUYER")
                    .content(buyerOffer.getText()).price(buyerBid).round(1).build());
            messages.add(NegotiationMessage.builder().role("SELLER")
                    .content(sellerOffer.getText()).price(sellerBid).round(1).build());

            if (buyerBid >= sellerBid) {
                double midPrice = (buyerBid + sellerBid) / 2;
                sessionSpan.setAttribute("outcome", "settled");
                sessionSpan.setAttribute("settlement.price", midPrice);
                PolicyContext settledCtx = PolicyContext.builder()
                        .originalPrice(service.getAskingPrice())
                        .buyerCeiling(service.getAskingPrice() * config.getBuyerStrategy().getCeiling())
                        .sellerFloor(service.getAskingPrice() * config.getSellerStrategy().getFloor())
                        .buyerOffer(buyerBid).sellerOffer(sellerBid)
                        .settlementPrice(midPrice)
                        .currentRound(1).maxRounds(1)
                        .negotiationId(negotiationId)
                        .protocol(NegotiateProtocol.SEALED_BID)
                        .build();
                anomalies.addAll(policyEngine.evaluate(settledCtx));
                return buildResult(true, midPrice, service, messages, 1, config,
                        negotiationId, trialIndex);
            } else {
                sessionSpan.setAttribute("outcome", "failed");
                PolicyContext ctx = PolicyContext.builder()
                        .originalPrice(service.getAskingPrice())
                        .buyerCeiling(service.getAskingPrice() * config.getBuyerStrategy().getCeiling())
                        .sellerFloor(service.getAskingPrice() * config.getSellerStrategy().getFloor())
                        .settlementPrice(0)
                        .buyerOffer(buyerBid).sellerOffer(sellerBid)
                        .currentRound(1).maxRounds(1)
                        .negotiationId(negotiationId)
                        .protocol(NegotiateProtocol.SEALED_BID)
                        .build();
                anomalies.addAll(policyEngine.evaluate(ctx));
                return buildResult(false, 0, service, messages, 1, config,
                        negotiationId, trialIndex);
            }
        } finally {
            sessionSpan.end();
        }
    }

    private String buildSealedBuyerContext(ServiceInfo service, NegotiationConfig config) {
        BuyerStrategy s = config.getBuyerStrategy();
        return String.format("""
                [密封出价谈判]
                商品：%s（%s）
                原始要价：$%.2f
                你的策略：%s（预算上限%.0f%%）
                规则：你和卖方同时出价，互不知道对方报价。
                如果你的出价 ≥ 卖方要价，以中间价成交；否则流拍。
                请给出你的最佳出价。
                """,
                service.getName(), service.getDescription(), service.getAskingPrice(),
                s.getDescription(), s.getCeiling() * 100);
    }

    private String buildSealedSellerContext(ServiceInfo service, NegotiationConfig config) {
        SellerStrategy s = config.getSellerStrategy();
        return String.format("""
                [密封出价谈判]
                商品：%s（%s）
                原始要价：$%.2f
                你的策略：%s（底线%.0f%%）
                规则：你和买方同时出价，互不知道对方报价。
                如果买方出价 ≥ 你的要价，以中间价成交；否则流拍。
                请给出你的最佳报价。
                """,
                service.getName(), service.getDescription(), service.getAskingPrice(),
                s.getDescription(), s.getFloor() * 100);
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
                .protocol(config.getProtocol() != null ? config.getProtocol().name() : "SEALED_BID")
                .negotiationId(negotiationId)
                .trialIndex(trialIndex > 0 ? trialIndex : null)
                .maxRounds(config.getMaxRounds())
                .convergenceThreshold(config.getConvergenceThreshold())
                .startedAt(OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME))
                .build();
    }
}
