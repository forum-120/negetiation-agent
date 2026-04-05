package com.nego.simulator.agent;


import com.nego.simulator.model.SellerStrategy;
import dev.langchain4j.agent.tool.Tool;

/**
 * 卖方工具类。
 *
 * 提供 LLM 可调用的动作：报价、接受、拒绝。
 * 内部根据 SellerStrategy 的参数做价格底线校验（硬约束），
 * 同时记录结构化报价，供 NegotiationService 直接读取。
 */
public class SellerTools {
    private final SellerStrategy strategy;
    private final double originalPrice;
    private double lastOffer = 0;
    private boolean accepted = false;

    public SellerTools(SellerStrategy strategy, double originalPrice) {
        this.strategy = strategy;
        this.originalPrice = originalPrice;
    }

    /**
     * 卖方报价。
     *
     * <p>LLM 通过该工具提交报价，工具内部会做价格底线校验：
     * 不允许低于最低接受价格，不允许无效报价。</p>
     *
     * @param price  本轮报价金额
     * @param reason 报价理由
     * @return 结构化反馈文本
     */
    @Tool("卖方报价。传入报价金额和报价理由。")
    public String makeOffer(double price, String reason) {
        double floor = originalPrice * strategy.getFloor();
        if (price <= 0) {
            return "无效报价，价格必须大于 0。";
        }
        if (price < floor) {
            price = floor;
        }
        this.lastOffer = price;
        return String.format("卖方报价 $%.2f（底线 $%.2f），理由：%s", price, floor, reason);
    }

    /**
     * 卖方接受对方报价，达成成交。
     *
     * @param price 接受的成交价格
     * @return 确认文本
     */
    @Tool("卖方接受对方报价，同意成交。传入接受的价格。")
    public String acceptDeal(double price) {
        this.accepted = true;
        this.lastOffer = price;
        return String.format("卖方接受报价 $%.2f，交易达成。", price);
    }

    /**
     * 卖方拒绝当前报价。
     *
     * @param reason 拒绝理由
     * @return 拒绝文本
     */
    @Tool("卖方拒绝对方报价。传入拒绝理由。")
    public String rejectDeal(String reason) {
        return "卖方拒绝当前报价，理由：" + reason;
    }



    public double getLastOffer() {
        return lastOffer;
    }
    public boolean isAccepted() {
        return accepted;
    }


}
