package com.nego.simulator.policy;

import com.nego.simulator.model.AnomalyRecord;
import com.nego.simulator.model.AnomalyType;
import com.nego.simulator.model.NegotiateProtocol;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PriceBoundaryRuleTest {

    private final PriceBoundaryRule rule = new PriceBoundaryRule();

    @Test
    void settlementPriceNormal_noAnomaly() {
        PolicyContext ctx = PolicyContext.builder()
                .settlementPrice(70).buyerCeiling(85).sellerFloor(55)
                .currentRound(3).maxRounds(8).negotiationId("test-001")
                .protocol(NegotiateProtocol.ITERATIVE_BARGAIN).build();
        List<AnomalyRecord> result = rule.check(ctx);
        assertThat(result).isEmpty();
    }

    @Test
    void settlementPriceAboveBuyerCeiling_triggersOverpayment() {
        PolicyContext ctx = PolicyContext.builder()
                .settlementPrice(90).buyerCeiling(85).sellerFloor(55)
                .currentRound(3).maxRounds(8).negotiationId("test-002")
                .protocol(NegotiateProtocol.ITERATIVE_BARGAIN).build();
        List<AnomalyRecord> result = rule.check(ctx);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType()).isEqualTo(AnomalyType.OVERPAYMENT);
        assertThat(result.get(0).getRelevantPrice()).isEqualTo(90.0);
        assertThat(result.get(0).getNegotiationId()).isEqualTo("test-002");
    }

    @Test
    void settlementPriceBelowSellerFloor_triggersOverpayment() {
        PolicyContext ctx = PolicyContext.builder()
                .settlementPrice(50).buyerCeiling(85).sellerFloor(55)
                .currentRound(3).maxRounds(8).negotiationId("test-003")
                .protocol(NegotiateProtocol.ITERATIVE_BARGAIN).build();
        List<AnomalyRecord> result = rule.check(ctx);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType()).isEqualTo(AnomalyType.OVERPAYMENT);
        assertThat(result.get(0).getRelevantPrice()).isEqualTo(50.0);
    }

    @Test
    void settlementPriceZero_noAnomaly() {
        PolicyContext ctx = PolicyContext.builder()
                .settlementPrice(0).buyerCeiling(85).sellerFloor(55)
                .currentRound(3).maxRounds(8).negotiationId("test-004")
                .protocol(NegotiateProtocol.ITERATIVE_BARGAIN).build();
        List<AnomalyRecord> result = rule.check(ctx);
        assertThat(result).isEmpty();
    }

    @Test
    void settlementPriceExactlyOnBoundary_noAnomaly() {
        PolicyContext ctx = PolicyContext.builder()
                .settlementPrice(85).buyerCeiling(85).sellerFloor(55)
                .currentRound(3).maxRounds(8).negotiationId("test-005")
                .protocol(NegotiateProtocol.ITERATIVE_BARGAIN).build();
        List<AnomalyRecord> result = rule.check(ctx);
        assertThat(result).isEmpty();
    }

    @Test
    void settlementPriceBothOutOfBounds_triggersTwoAnomalies() {
        PolicyContext ctx = PolicyContext.builder()
                .settlementPrice(90).buyerCeiling(85).sellerFloor(95)
                .currentRound(3).maxRounds(8).negotiationId("test-006")
                .protocol(NegotiateProtocol.ITERATIVE_BARGAIN).build();
        List<AnomalyRecord> result = rule.check(ctx);
        assertThat(result).hasSize(2);
    }
}