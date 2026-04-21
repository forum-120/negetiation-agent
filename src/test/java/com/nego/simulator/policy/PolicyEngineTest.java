package com.nego.simulator.policy;

import com.nego.simulator.model.AnomalyRecord;
import com.nego.simulator.model.AnomalyType;
import com.nego.simulator.model.NegotiateProtocol;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyEngineTest {

    @Test
    void multipleRulesTrigger_resultsAreAggregated() {
        PriceBoundaryRule priceBoundaryRule = new PriceBoundaryRule();
        DeadlockRule deadlockRule = new DeadlockRule();
        PolicyEngine engine = new PolicyEngine(List.of(priceBoundaryRule, deadlockRule));

        PolicyContext ctx = PolicyContext.builder()
                .settlementPrice(0).currentRound(8).maxRounds(8)
                .buyerOffer(60).sellerOffer(80)
                .buyerCeiling(85).sellerFloor(55)
                .negotiationId("test-001")
                .protocol(NegotiateProtocol.ITERATIVE_BARGAIN).build();
        List<AnomalyRecord> result = engine.evaluate(ctx);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType()).isEqualTo(AnomalyType.DEADLOCK);
    }

    @Test
    void noRulesMatch_emptyResult() {
        PolicyEngine engine = new PolicyEngine(List.of(new PriceBoundaryRule(), new DeadlockRule()));

        PolicyContext ctx = PolicyContext.builder()
                .settlementPrice(70).buyerCeiling(85).sellerFloor(55)
                .currentRound(3).maxRounds(8)
                .negotiationId("test-002")
                .protocol(NegotiateProtocol.ITERATIVE_BARGAIN).build();
        List<AnomalyRecord> result = engine.evaluate(ctx);
        assertThat(result).isEmpty();
    }

    @Test
    void deadlockAndOverpaymentBothTrigger() {
        PolicyEngine engine = new PolicyEngine(List.of(
                new PriceBoundaryRule(), new DeadlockRule(), new ConstraintViolationRule()));

        PolicyContext ctx = PolicyContext.builder()
                .settlementPrice(0).currentRound(5).maxRounds(5)
                .buyerOffer(120).sellerOffer(30)
                .buyerCeiling(100).sellerFloor(50)
                .originalPrice(100)
                .negotiationId("test-003")
                .protocol(NegotiateProtocol.ITERATIVE_BARGAIN).build();
        List<AnomalyRecord> result = engine.evaluate(ctx);
        assertThat(result.size()).isGreaterThanOrEqualTo(2);
    }
}