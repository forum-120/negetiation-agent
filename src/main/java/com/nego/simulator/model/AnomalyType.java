package com.nego.simulator.model;

public enum AnomalyType {
    OVERPAYMENT, // 超额支付（收敛中间价超出任一方策略设定的硬边界）
    CONSTRAINT_VIOLATION, // 约束违规 （试图突破硬边界，被Guardrail拦截）
    DEADLOCK // 谈判僵局（达到最大轮数未成交）

}
