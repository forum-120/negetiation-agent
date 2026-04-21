package com.nego.simulator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 谈判结果领域模型。
 *
 * <p>
 * 该类用于表示一场完整谈判结束后的最终输出结果，
 * 是控制层返回给前端页面、历史记录模块和统计分析模块的核心数据对象。
 * </p>
 *
 * <p>
 * 这版设计采用轻量工程方案，不追求复杂架构，
 * 重点是把“谈判是否成功、最终价格是多少、用了什么策略、过程消息有哪些”这些关键信息表达清楚。
 * </p>
 *
 * <p>
 * 通过这个对象，系统可以很直观地回答以下问题：
 * </p>
 * <ul>
 * <li>这场谈判最终是否成交</li>
 * <li>成交价格是多少</li>
 * <li>相对原始价格优惠了多少</li>
 * <li>总共进行了多少轮</li>
 * <li>买方和卖方分别采用了什么策略</li>
 * <li>双方对最终结果的满意度如何</li>
 * <li>完整的谈判过程消息是什么</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NegotiationResult {

    /**
     * 是否达成成交。
     *
     * <p>
     * true 表示买卖双方最终达成一致；
     * false 表示谈判失败、达到最大轮数仍未成交，或者没有满足收敛条件。
     * </p>
     */
    private Boolean agreed;

    /**
     * 最终成交价格。
     *
     * <p>
     * 如果谈判成功，该字段表示最后达成一致的成交价；
     * 如果谈判失败，该字段可以为空。
     * </p>
     */
    private Double finalPrice;

    /**
     * 原始标价。
     *
     * <p>
     * 通常来自 {@link ServiceInfo} 的 askingPrice，
     * 用于和 finalPrice 对比，从而计算折扣率和议价效果。
     * </p>
     */
    private Double originalPrice;

    /**
     * 折扣率。
     *
     * <p>
     * 表示最终成交价格相对于原始标价的优惠程度。
     * 例如 0.20 表示优惠了 20%。
     * </p>
     */
    private Double discount;

    /**
     * 实际谈判轮数。
     *
     * <p>
     * 表示从开始到结束一共进行了多少轮报价与反报价，
     * 可用于衡量不同策略的谈判效率。
     * </p>
     */
    private Integer rounds;

    /**
     * 本次谈判的完整消息历史。
     *
     * <p>
     * 按时间顺序记录买方、卖方以及系统消息，
     * 便于前端展示谈判回放，也便于后续调试和历史查询。
     * </p>
     */
    private List<NegotiationMessage> messages;

    /**
     * 买方满意度。
     *
     * <p>
     * 用于从买方视角评价本次谈判结果是否理想。
     * 一般可以根据最终成交价、预算压力和谈判轮数综合计算。
     * </p>
     */
    private Double buyerSatisfaction;

    /**
     * 卖方满意度。
     *
     * <p>
     * 用于从卖方视角评价本次谈判结果是否理想。
     * 一般可以根据最终成交价、让步幅度和成交效率综合计算。
     * </p>
     */
    private Double sellerSatisfaction;

    /**
     * 买方策略名称。
     *
     * <p>
     * 用于记录本次谈判中买方采用的是哪一种策略，
     * 方便在历史记录和统计分析中按策略维度做对比。
     * </p>
     */
    private String buyerStrategy;

    /**
     * 卖方策略名称。
     *
     * <p>
     * 用于记录本次谈判中卖方采用的是哪一种策略，
     * 方便在历史记录和统计分析中按策略维度做对比。
     * </p>
     */
    private String sellerStrategy;
    
    /**
     * RAG模式。
     */
    private String ragMode;
    
    /**
     * 商品ID。
     */
    private String serviceId;

    @Builder.Default
    private List<AnomalyRecord> anomalyRecords = new ArrayList<>();

    private Double nashWelfare;
    private String protocol;
    private String negotiationId;
    private Integer trialIndex;
    private Integer maxRounds;
    private Double convergenceThreshold;
    private String startedAt;
}
