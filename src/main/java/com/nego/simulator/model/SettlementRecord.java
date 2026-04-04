package com.nego.simulator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 结算记录模型。
 *
 * <p>这个类用于表示一次谈判成功后的简化结算结果，
 * 采用“实习友好版”设计，只保留最核心、最容易讲清楚的字段。</p>
 *
 * <p>当前阶段不会接入真实支付系统，而是用这个对象来模拟：
 * 某一场谈判在达成一致后，系统记录了一条成交结果。</p>
 *
 * <p>它的主要作用有两个：</p>
 * <ul>
 *     <li>给系统补上“谈判完成后还有结算记录”的业务闭环</li>
 *     <li>为后续日志展示、历史查询和接口返回提供统一的数据结构</li>
 * </ul>
 *
 * <p>这版模型刻意不引入买方地址、卖方地址等更复杂字段，
 * 目的是控制项目复杂度，保证整体结构清晰、容易理解，也更适合暑期实习项目讲解。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementRecord {

    /**
     * 谈判记录唯一标识。
     *
     * <p>用于说明这条结算记录对应的是哪一场谈判，
     * 便于后续把谈判结果和结算结果关联起来。</p>
     */
    private String negotiationId;

    /**
     * 服务名称。
     *
     * <p>用于记录本次成交对应的商品或服务名称，
     * 这样在查看结算日志或历史记录时，可以直接知道交易对象是什么。</p>
     */
    private String serviceName;

    /**
     * 最终成交价格。
     *
     * <p>表示买卖双方最终达成一致后用于结算的金额。</p>
     */
    private Double finalPrice;

    /**
     * 结算状态。
     *
     * <p>用于描述当前记录所处的状态，例如：
     * SUCCESS（结算成功）或 FAILED（结算失败）。</p>
     *
     * <p>在当前项目里，这个字段主要用于演示交易闭环和状态表达。</p>
     */
    private String status;

    /**
     * 结算记录生成时间。
     *
     * <p>用于记录这条结算信息是什么时候创建的，
     * 便于后续排序、展示和简单审计。</p>
     */
    private LocalDateTime timestamp;
}
