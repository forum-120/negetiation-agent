package com.nego.simulator.policy;

import com.nego.simulator.model.AnomalyRecord;
import com.nego.simulator.model.AnomalyType;
import com.nego.simulator.model.NegotiateProtocol;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConstraintViolationRuleTest {

    private final ConstraintViolationRule rule = new ConstraintViolationRule();

    @Test
    void buyerOfferNormal_noViolation() {
        PolicyContext ctx = PolicyContext.builder()
                .buyerOffer(80).buyerCeiling(100).sellerOffer(60).sellerFloor(50)
                .currentRound(2).negotiationId("test-001")
                .protocol(NegotiateProtocol.ITERATIVE_BARGAIN).build();
        List<AnomalyRecord> result = rule.check(ctx);
        assertThat(result).isEmpty();
    }

    @Test
    void buyerOfferAboveCeiling1percent_triggersViolation() {
        PolicyContext ctx = PolicyContext.builder()
                .buyerOffer(102).buyerCeiling(100).sellerOffer(60).sellerFloor(50)
                .currentRound(2).negotiationId("test-002")
                .protocol(NegotiateProtocol.ITERATIVE_BARGAIN).build();
        List<AnomalyRecord> result = rule.check(ctx);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType()).isEqualTo(AnomalyType.CONSTRAINT_VIOLATION);
        assertThat(result.get(0).getDescription()).contains("买方");
    }

    @Test
    void sellerOfferBelowFloor1percent_triggersViolation() {
        PolicyContext ctx = PolicyContext.builder()
                .buyerOffer(80).buyerCeiling(100).sellerOffer(49).sellerFloor(50)
                .currentRound(2).negotiationId("test-003")
                .protocol(NegotiateProtocol.ITERATIVE_BARGAIN).build();
        List<AnomalyRecord> result = rule.check(ctx);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType()).isEqualTo(AnomalyType.CONSTRAINT_VIOLATION);
        assertThat(result.get(0).getDescription()).contains("卖方");
    }

    @Test
    void bothNormal_noViolation() {
        PolicyContext ctx = PolicyContext.builder()
                .buyerOffer(90).buyerCeiling(100).sellerOffer(60).sellerFloor(50)
                .currentRound(2).negotiationId("test-004")
                .protocol(NegotiateProtocol.ITERATIVE_BARGAIN).build();
        List<AnomalyRecord> result = rule.check(ctx);
        assertThat(result).isEmpty();
    }

    @Test
    void zeroOffers_noViolation() {
        PolicyContext ctx = PolicyContext.builder()
                .buyerOffer(0).buyerCeiling(100).sellerOffer(0).sellerFloor(50)
                .currentRound(1).negotiationId("test-005")
                .protocol(NegotiateProtocol.ITERATIVE_BARGAIN).build();
        List<AnomalyRecord> result = rule.check(ctx);
        assertThat(result).isEmpty();
    }

    @Test
    void buyerOfferWithin1percent_noViolation() {
        PolicyContext ctx = PolicyContext.builder()
                .buyerOffer(100.5).buyerCeiling(100).sellerOffer(60).sellerFloor(50)
                .currentRound(2).negotiationId("test-006")
                .protocol(NegotiateProtocol.ITERATIVE_BARGAIN).build();
        List<AnomalyRecord> result = rule.check(ctx);
        assertThat(result).isEmpty();
    }

    @Test
    void sellerOfferWithin1percent_noViolation() {
        PolicyContext ctx = PolicyContext.builder()
                .buyerOffer(80).buyerCeiling(100).sellerOffer(50.4).sellerFloor(50)
                .currentRound(2).negotiationId("test-007")
                .protocol(NegotiateProtocol.ITERATIVE_BARGAIN).build();
        List<AnomalyRecord> result = rule.check(ctx);
        assertThat(result).isEmpty();
    }
}