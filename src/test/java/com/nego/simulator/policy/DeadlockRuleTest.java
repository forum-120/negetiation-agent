package com.nego.simulator.policy;

import com.nego.simulator.model.AnomalyRecord;
import com.nego.simulator.model.AnomalyType;
import com.nego.simulator.model.NegotiateProtocol;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeadlockRuleTest {

    private final DeadlockRule rule = new DeadlockRule();

    @Test
    void iterativeNotMaxRounds_noDeadlock() {
        PolicyContext ctx = PolicyContext.builder()
                .settlementPrice(0).currentRound(3).maxRounds(8)
                .negotiationId("test-001")
                .protocol(NegotiateProtocol.ITERATIVE_BARGAIN).build();
        List<AnomalyRecord> result = rule.check(ctx);
        assertThat(result).isEmpty();
    }

    @Test
    void iterativeMaxRoundsNotConverged_triggersDeadlock() {
        PolicyContext ctx = PolicyContext.builder()
                .settlementPrice(0).currentRound(8).maxRounds(8)
                .buyerOffer(60).sellerOffer(80)
                .negotiationId("test-002")
                .protocol(NegotiateProtocol.ITERATIVE_BARGAIN).build();
        List<AnomalyRecord> result = rule.check(ctx);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType()).isEqualTo(AnomalyType.DEADLOCK);
        assertThat(result.get(0).getDescription()).contains("Iterative");
    }

    @Test
    void sealedBidFailed_triggersDeadlock_withBothPrices() {
        PolicyContext ctx = PolicyContext.builder()
                .settlementPrice(0).currentRound(1).maxRounds(1)
                .buyerOffer(50).sellerOffer(70)
                .negotiationId("test-003")
                .protocol(NegotiateProtocol.SEALED_BID).build();
        List<AnomalyRecord> result = rule.check(ctx);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType()).isEqualTo(AnomalyType.DEADLOCK);
        assertThat(result.get(0).getDescription()).contains("Sealed-Bid");
        assertThat(result.get(0).getDescription()).contains("$50");
        assertThat(result.get(0).getDescription()).contains("$70");
    }

    @Test
    void oneShotRejected_triggersDeadlock() {
        PolicyContext ctx = PolicyContext.builder()
                .settlementPrice(0).currentRound(1).maxRounds(1)
                .negotiationId("test-004")
                .protocol(NegotiateProtocol.ONE_SHOT).build();
        List<AnomalyRecord> result = rule.check(ctx);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType()).isEqualTo(AnomalyType.DEADLOCK);
        assertThat(result.get(0).getDescription()).contains("One-Shot");
    }

    @Test
    void settled_notDeadlock() {
        PolicyContext ctx = PolicyContext.builder()
                .settlementPrice(75).currentRound(1).maxRounds(1)
                .negotiationId("test-005")
                .protocol(NegotiateProtocol.SEALED_BID).build();
        List<AnomalyRecord> result = rule.check(ctx);
        assertThat(result).isEmpty();
    }
}