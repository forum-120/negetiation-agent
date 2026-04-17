package com.nego.simulator.agent;

import com.nego.simulator.model.BuyerStrategy;


import com.nego.simulator.model.AnomalyRecord;
import com.nego.simulator.model.AnomalyType;
import dev.langchain4j.agent.tool.Tool;

import java.util.ArrayList;
import java.util.List;

/**
 * 买方工具类
 * 提供LLM tools：出价，接受，拒绝
 * 同时记录结构化报价，给NegotiationService读取
 */
public class BuyerTools {

    private final BuyerStrategy strategy;
    private final double originalPrice;
    private double lastOffer = 0;
    private double opponentLastOffer = 0;
    private boolean accepted = false;
    private final List<AnomalyRecord> violations = new ArrayList<>();

    public void setOpponentLastOffer(double opponentLastOffer) {
        this.opponentLastOffer = opponentLastOffer;
    }

    public BuyerTools(BuyerStrategy strategy, double originalPrice) {
        this.strategy = strategy;
        this.originalPrice = originalPrice;
    }

    /**
     * 买方出价
     * LLM通过该tool提交报价，工具内部做价格边界校验
     * 不允许超过预算上限和无效报价
     *
     * @param price  本轮报价金额
     * @param reason 出价理由
     * @return 结构化反馈文本
     */
    @Tool("买方出价，传入报价金额和出价理由。")
    public String makeOffer(double price, String reason) {
        double ceiling = originalPrice * strategy.getCeiling();

        if (price <= 0) {
            violations.add(AnomalyRecord.builder()
                .type(AnomalyType.CONSTRAINT_VIOLATION)
                .description("买方给出了无效负数或零报价: " + price)
                .relevantPrice(price)
                .build());
            return "无效报价，价格必须大于0！";
        }

        if (price > ceiling) {
            violations.add(AnomalyRecord.builder()
                .type(AnomalyType.CONSTRAINT_VIOLATION)
                .description("买方越界出价 $" + price + " (系统强制修正为上限 $" + ceiling + ")")
                .relevantPrice(price)
                .build());
            price = ceiling;
        }

        this.lastOffer = price;
        return String.format("买方出价$%.2f(上限$%.2f),理由：%s", price, ceiling, reason);
    }

    /**
     * 买方接受对方最新报价，达成成交。
     * 工具恢复了价格参数以防止框架反射报错，但在内部被强制忽略，防止作弊。
     *
     * @param price 填入的假定价格
     * @return 确认文本
     */
    @Tool("买方接受对方最新报价，同意以此前对方的最新出价直接成交。调用此方法即代表完全妥协！请传入对方最新的报价金额作为参数。")
    public String acceptDeal(double price) {
        this.accepted = true;
        // 防作弊核心：无视大模型传来的幻觉参数 price，强制绑定对手刚才的真实报价！
        this.lastOffer = this.opponentLastOffer;
        return String.format("买方接受了对方的最新报价 $%.2f，交易达成。", this.opponentLastOffer);
    }

    /**
     * 买方拒绝当前报价。
     *
     * @param reason 拒绝理由
     * @return 拒绝文本
     */
    @Tool("买方拒绝对方报价。传入拒绝理由。")
    public String rejectDeal(String reason) {
        return "买方拒绝当前报价，理由：" + reason;
    }

    public double getLastOffer() {
        return lastOffer;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public List<AnomalyRecord> getViolations() {
        return violations;
    }

}
