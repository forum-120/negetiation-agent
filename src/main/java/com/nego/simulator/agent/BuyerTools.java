package com.nego.simulator.agent;

import com.nego.simulator.model.BuyerStrategy;
import dev.langchain4j.agent.tool.Tool;

/**
 * 买方工具类
 * 提供LLM tools：出价，接受，拒绝
 * 同时记录结构化报价，给NegotiationService读取
 */
public class BuyerTools {

    private final BuyerStrategy strategy;
    private final double originalPrice;
    private double lastOffer = 0;
    private boolean accepted = false;



    public BuyerTools(BuyerStrategy strategy, double originalPrice) {
        this.strategy = strategy;
        this.originalPrice = originalPrice;
    }

    /**
     * 买方出价
     * LLM通过该tool提交报价，工具内部做价格边界校验
     * 不允许超过预算上限和无效报价
     *
     * @param price 本轮报价金额
     * @param reason 出价理由
     * @return 结构化反馈文本
     */
    @Tool("买方出价，传入报价金额和出价理由。")
    public String makeOffer(double price, String reason) {
        double ceiling = originalPrice * strategy.getCeiling();

        if(price<=0) {
            return "无效报价，价格必须大于0！";
        }

        if(price > ceiling) {
            price = ceiling;
        }

        this.lastOffer = price;
        return String.format("买方出价$.2f(上限$.2f),理由：%s", price, ceiling, reason);
    }

    /**
     * 买方接受对方报价，达成成交。
     *
     * @param price 接受的成交价格
     * @return 确认文本
     */
    @Tool("买方接受对方报价，同意成交。传入接受的价格。")
    public String acceptDeal(double price) {
        this.accepted = true;
        this.lastOffer = price;
        return String.format("买方接受报价 $%.2f，交易达成。", price);
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



}
