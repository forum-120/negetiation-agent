package com.nego.simulator.policy;

import com.nego.simulator.model.AnomalyRecord;
import com.nego.simulator.model.AnomalyType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DeadlockRule implements PolicyRule {

    @Override
    public List<AnomalyRecord> check(PolicyContext ctx) {
        List<AnomalyRecord> anomalies = new ArrayList<>();

        boolean isDeadlock = ctx.getSettlementPrice() <= 0
                && ctx.getCurrentRound() >= ctx.getMaxRounds();

        if (isDeadlock) {
            String desc = switch (ctx.getProtocol()) {
                case SEALED_BID -> String.format(
                        "Sealed-Bid 流拍：买方出价 $%.2f < 卖方报价 $%.2f，双方价格未能交叉",
                        ctx.getBuyerOffer(), ctx.getSellerOffer());
                case ONE_SHOT -> "One-Shot 买方拒绝，未达成交易";
                default -> String.format(
                        "Iterative 谈判陷入僵局：达到最大轮数 %d 轮仍未收敛",
                        ctx.getMaxRounds());
            };

            anomalies.add(AnomalyRecord.builder()
                    .type(AnomalyType.DEADLOCK)
                    .description(desc)
                    .round(ctx.getCurrentRound())
                    .negotiationId(ctx.getNegotiationId())
                    .timestamp(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
                            .format(java.time.format.DateTimeFormatter.ISO_DATE_TIME))
                    .build());
        }

        return anomalies;
    }
}