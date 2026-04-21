package com.nego.simulator.protocol;

import com.nego.simulator.model.AnomalyRecord;
import com.nego.simulator.model.BuyerStrategy;
import com.nego.simulator.model.NegotiateProtocol;
import com.nego.simulator.model.NegotiationConfig;
import com.nego.simulator.model.NegotiationMessage;
import com.nego.simulator.model.OfferResponse;
import com.nego.simulator.model.RagMode;
import com.nego.simulator.model.SellerStrategy;
import com.nego.simulator.model.ServiceInfo;
import com.nego.simulator.policy.ConstraintViolationRule;
import com.nego.simulator.policy.DeadlockRule;
import com.nego.simulator.policy.PolicyEngine;
import com.nego.simulator.policy.PriceBoundaryRule;
import com.nego.simulator.service.AgentTransport;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class IterativeBargainExecutorTest {

    @Test
    void sellerRejectWithoutCounteroffer_keepsPreviousStandingOffer() {
        IterativeBargainExecutor executor = new IterativeBargainExecutor(
                null,
                OpenTelemetry.noop().getTracer("test"),
                new PolicyEngine(List.of(
                        new ConstraintViolationRule(),
                        new PriceBoundaryRule(),
                        new DeadlockRule()))
        );

        StubTransport transport = new StubTransport(
                List.of(
                        offer(32.0, false, "buyer opens"),
                        offer(40.0, false, "buyer raises")
                ),
                List.of(
                        offer(0.0, false, "seller rejects without counteroffer"),
                        offer(72.0, false, "seller finally counters")
                )
        );

        ServiceInfo service = ServiceInfo.builder()
                .id("svc-cheap")
                .name("数据标注服务")
                .description("基础数据标注")
                .askingPrice(80.0)
                .category("数据服务")
                .build();

        NegotiationConfig config = NegotiationConfig.builder()
                .buyerStrategy(BuyerStrategy.AGGRESSIVE)
                .sellerStrategy(SellerStrategy.PREMIUM)
                .ragMode(RagMode.NONE)
                .protocol(NegotiateProtocol.ITERATIVE_BARGAIN)
                .maxRounds(2)
                .convergenceThreshold(0.03)
                .build();

        var result = executor.execute(service, config, transport, new ArrayList<>(), "neg-1", 0);

        assertThat(result.getAgreed()).isFalse();
        assertThat(transport.buyerContexts).hasSize(2);
        assertThat(transport.buyerContexts.get(1)).contains("对方上一轮出价：$80.00");
        assertThat(transport.buyerContexts.get(1)).doesNotContain("对方上一轮出价：尚未出价");
        assertThat(result.getMessages())
                .extracting(NegotiationMessage::getPrice)
                .containsExactly(32.0, 80.0, 40.0, 72.0);
    }

    private static OfferResponse offer(double price, boolean accepted, String text) {
        return OfferResponse.builder()
                .sessionId("test-session")
                .text(text)
                .lastOffer(price)
                .accepted(accepted)
                .violations(List.of())
                .build();
    }

    private static final class StubTransport implements AgentTransport {
        private final Queue<OfferResponse> buyerOffers;
        private final Queue<OfferResponse> sellerOffers;
        private final List<String> buyerContexts = new ArrayList<>();
        private final List<String> sellerContexts = new ArrayList<>();

        private StubTransport(List<OfferResponse> buyerOffers, List<OfferResponse> sellerOffers) {
            this.buyerOffers = new ArrayDeque<>(buyerOffers);
            this.sellerOffers = new ArrayDeque<>(sellerOffers);
        }

        @Override
        public CompletableFuture<OfferResponse> sendToBuyer(String context) {
            buyerContexts.add(context);
            return CompletableFuture.completedFuture(buyerOffers.remove());
        }

        @Override
        public CompletableFuture<OfferResponse> sendToSeller(String context) {
            sellerContexts.add(context);
            return CompletableFuture.completedFuture(sellerOffers.remove());
        }

        @Override
        public void setOpponentOfferForBuyer(double sellerPrice) {
        }

        @Override
        public void setOpponentOfferForSeller(double buyerPrice) {
        }

        @Override
        public List<AnomalyRecord> getBuyerViolations() {
            return List.of();
        }

        @Override
        public List<AnomalyRecord> getSellerViolations() {
            return List.of();
        }

        @Override
        public void close() {
        }
    }
}
