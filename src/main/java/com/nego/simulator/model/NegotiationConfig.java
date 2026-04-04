package com.nego.simulator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 谈判配置模型。
 *
 * <p>这个类用于承载一场谈判运行时所需的核心参数，
 * 是当前项目里一个“轻量但有用”的配置对象。</p>
 *
 * <p>保留这个类的目的不是为了做复杂架构，而是为了让代码更清楚：</p>
 * <ul>
 *     <li>把一次谈判的运行参数集中起来管理</li>
 *     <li>避免业务方法参数过多、过散</li>
 *     <li>让 Controller 和 Service 之间的数据传递更清晰</li>
 * </ul>
 *
 * <p>在这版实现里，我们不引入复杂设计模式，
 * 而是直接使用策略枚举来表示买方和卖方的谈判风格。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NegotiationConfig {

    /**
     * 买方策略。
     *
     * <p>用于表示本次谈判中买方采用哪一种出价风格。
     * 例如：激进型、保守型。</p>
     */
    private BuyerStrategy buyerStrategy;

    /**
     * 卖方策略。
     *
     * <p>用于表示本次谈判中卖方采用哪一种议价风格。
     * 例如：坚挺型、灵活型。</p>
     */
    private SellerStrategy sellerStrategy;

    /**
     * 最大谈判轮数。
     *
     * <p>用于限制买卖双方最多进行多少轮交互。
     * 如果达到该轮数仍未成交，则本次谈判结束。</p>
     */
    private Integer maxRounds;

    /**
     * 收敛阈值。
     *
     * <p>用于判断买方报价与卖方报价之间是否已经足够接近。
     * 当双方价格差距低于该阈值时，系统可以认为价格基本收敛，
     * 从而触发自动成交或中间价成交逻辑。</p>
     *
     * <p>该值通常使用比例表示，例如：
     * <ul>
     *     <li>0.05 表示 5%</li>
     *     <li>0.03 表示 3%</li>
     * </ul>
     */
    private Double convergenceThreshold;
}
