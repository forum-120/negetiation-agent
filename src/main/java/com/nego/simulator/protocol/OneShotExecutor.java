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
import java.util.concurrent.TimeUnit;

@Component
@SupportsProtocol(NegotiateProtocol.ONE_SHOT)
public class OneShotExecutor implements ProtocolExecutor {

    private final Tracer tracer;
    private final PolicyEngine policyEngine;

    public OneShotExecutor(Tracer tracer, PolicyEngine policyEngine) {
        this.tracer = tracer;
        this.policyEngine = policyEngine;
    }

    @Override
    public NegotiationResult execute(ServiceInfo service, NegotiationConfig config,
                                     AgentTransport transport, List<AnomalyRecord> anomalies,
                                     String negotiationId, int trialIndex) {

        String sellerContext = buildOneShotSellerContext(service, config);
        transport.setOpponentOfferForSeller(0);

        Span sellerSpan = tracer.spanBuilder("one-shot.seller.propose").startSpan();
        OfferResponse sellerOffer;
        try (Scope scope = sellerSpan.makeCurrent()) {
            try {
                sellerOffer = transport.sendToSeller(sellerContext).get(90, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Seller agent call interrupted", e);
            } catch (Exception e) {
                throw new RuntimeException("Seller agent call failed or timed out", e);
            }
        } finally {
            sellerSpan.end();
        }

        double sellerPrice = sellerOffer.getLastOffer();

        String buyerContext = buildOneShotBuyerContext(service, config, sellerPrice);
        transport.setOpponentOfferForBuyer(sellerPrice);

        Span buyerSpan = tracer.spanBuilder("one-shot.buyer.decide").startSpan();
        OfferResponse buyerOffer;
        try (Scope scope = buyerSpan.makeCurrent()) {
            try {
                buyerOffer = transport.sendToBuyer(buyerContext).get(90, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Buyer agent call interrupted", e);
            } catch (Exception e) {
                throw new RuntimeException("Buyer agent call failed or timed out", e);
            }
        } finally {
            buyerSpan.end();
        }

        List<NegotiationMessage> messages = new ArrayList<>();
        messages.add(NegotiationMessage.builder().role("SELLER")
                .content(sellerOffer.getText()).price(sellerPrice).round(1).build());
        messages.add(NegotiationMessage.builder().role("BUYER")
                .content(buyerOffer.getText()).price(buyerOffer.getLastOffer()).round(1).build());

        if (buyerOffer.isAccepted()) {
            PolicyContext settledCtx = PolicyContext.builder()
                    .originalPrice(service.getAskingPrice())
                    .buyerCeiling(service.getAskingPrice() * config.getBuyerStrategy().getCeiling())
                    .sellerFloor(service.getAskingPrice() * config.getSellerStrategy().getFloor())
                    .buyerOffer(buyerOffer.getLastOffer()).sellerOffer(sellerPrice)
                    .settlementPrice(sellerPrice)
                    .currentRound(1).maxRounds(1)
                    .negotiationId(negotiationId)
                    .protocol(NegotiateProtocol.ONE_SHOT)
                    .build();
            anomalies.addAll(policyEngine.evaluate(settledCtx));
            return buildResult(true, sellerPrice, service, messages, 1, config,
                    negotiationId, trialIndex);
        } else {
            PolicyContext ctx = PolicyContext.builder()
                    .originalPrice(service.getAskingPrice())
                    .buyerCeiling(service.getAskingPrice() * config.getBuyerStrategy().getCeiling())
                    .sellerFloor(service.getAskingPrice() * config.getSellerStrategy().getFloor())
                    .buyerOffer(buyerOffer.getLastOffer()).sellerOffer(sellerPrice)
                    .settlementPrice(0)
                    .currentRound(1).maxRounds(1)
                    .negotiationId(negotiationId)
                    .protocol(NegotiateProtocol.ONE_SHOT)
                    .build();
            anomalies.addAll(policyEngine.evaluate(ctx));
            return buildResult(false, 0, service, messages, 1, config,
                    negotiationId, trialIndex);
        }
    }

    private String buildOneShotBuyerContext(ServiceInfo service, NegotiationConfig config, double sellerPrice) {
        BuyerStrategy s = config.getBuyerStrategy();
        return String.format("""
                [一次性报价决策]
                商品：%s（%s）
                原始要价：$%.2f
                卖方报价：$%.2f
                你的预算上限：$%.2f（%.0f%%）
                规则：你只能接受或拒绝此报价，不能还价。
                请决定是否接受。
                """,
                service.getName(), service.getDescription(), service.getAskingPrice(),
                sellerPrice,
                service.getAskingPrice() * s.getCeiling(), s.getCeiling() * 100);
    }

    private String buildOneShotSellerContext(ServiceInfo service, NegotiationConfig config) {
        SellerStrategy s = config.getSellerStrategy();
        return String.format("""
                [一次性报价]
                商品：%s（%s）
                原始要价：$%.2f
                你的底线：$%.2f（%.0f%%）
                规则：你给出一个报价，买方只能接受或拒绝。请给出你的报价。
                """,
                service.getName(), service.getDescription(), service.getAskingPrice(),
                service.getAskingPrice() * s.getFloor(), s.getFloor() * 100);
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
                .protocol(config.getProtocol() != null ? config.getProtocol().name() : "ONE_SHOT")
                .negotiationId(negotiationId)
                .trialIndex(trialIndex > 0 ? trialIndex : null)
                .maxRounds(config.getMaxRounds())
                .convergenceThreshold(config.getConvergenceThreshold())
                .startedAt(OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME))
                .build();
    }
}
