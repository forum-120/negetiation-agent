package com.nego.simulator.policy;

import com.nego.simulator.model.AnomalyRecord;
import com.nego.simulator.model.AnomalyType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PriceBoundaryRule implements PolicyRule {

    @Override
    public List<AnomalyRecord> check(PolicyContext ctx) {
        List<AnomalyRecord> anomalies = new ArrayList<>();
        double price = ctx.getSettlementPrice();
        if (price <= 0) return anomalies;

        if (price > ctx.getBuyerCeiling()) {
            anomalies.add(AnomalyRecord.builder()
                    .type(AnomalyType.OVERPAYMENT)
                    .description(String.format(
                            "成交价 $%.2f 超过买方预算上限 $%.2f（%.1f%%）",
                            price, ctx.getBuyerCeiling(),
                            (price - ctx.getBuyerCeiling()) / ctx.getBuyerCeiling() * 100))
                    .round(ctx.getCurrentRound())
                    .relevantPrice(price)
                    .negotiationId(ctx.getNegotiationId())
                    .timestamp(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
                            .format(java.time.format.DateTimeFormatter.ISO_DATE_TIME))
                    .build());
        }

        if (price < ctx.getSellerFloor()) {
            anomalies.add(AnomalyRecord.builder()
                    .type(AnomalyType.OVERPAYMENT)
                    .description(String.format(
                            "成交价 $%.2f 低于卖方底线 $%.2f（%.1f%%）",
                            price, ctx.getSellerFloor(),
                            (ctx.getSellerFloor() - price) / ctx.getSellerFloor() * 100))
                    .round(ctx.getCurrentRound())
                    .relevantPrice(price)
                    .negotiationId(ctx.getNegotiationId())
                    .timestamp(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
                            .format(java.time.format.DateTimeFormatter.ISO_DATE_TIME))
                    .build());
        }

        return anomalies;
    }
}