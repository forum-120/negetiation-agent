package com.nego.simulator.policy;

import com.nego.simulator.model.AnomalyRecord;
import com.nego.simulator.model.AnomalyType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ConstraintViolationRule implements PolicyRule {

    @Override
    public List<AnomalyRecord> check(PolicyContext ctx) {
        List<AnomalyRecord> anomalies = new ArrayList<>();

        if (ctx.getBuyerOffer() > 0 && ctx.getBuyerOffer() > ctx.getBuyerCeiling() * 1.01) {
            anomalies.add(AnomalyRecord.builder()
                    .type(AnomalyType.CONSTRAINT_VIOLATION)
                    .description(String.format(
                            "[Executor层] 买方出价 $%.2f 仍高于上限 $%.2f（Tools 修正失效？）",
                            ctx.getBuyerOffer(), ctx.getBuyerCeiling()))
                    .round(ctx.getCurrentRound())
                    .relevantPrice(ctx.getBuyerOffer())
                    .negotiationId(ctx.getNegotiationId())
                    .timestamp(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
                            .format(java.time.format.DateTimeFormatter.ISO_DATE_TIME))
                    .build());
        }

        if (ctx.getSellerOffer() > 0 && ctx.getSellerOffer() < ctx.getSellerFloor() * 0.99) {
            anomalies.add(AnomalyRecord.builder()
                    .type(AnomalyType.CONSTRAINT_VIOLATION)
                    .description(String.format(
                            "[Executor层] 卖方报价 $%.2f 仍低于底线 $%.2f（Tools 修正失效？）",
                            ctx.getSellerOffer(), ctx.getSellerFloor()))
                    .round(ctx.getCurrentRound())
                    .relevantPrice(ctx.getSellerOffer())
                    .negotiationId(ctx.getNegotiationId())
                    .timestamp(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
                            .format(java.time.format.DateTimeFormatter.ISO_DATE_TIME))
                    .build());
        }

        return anomalies;
    }
}