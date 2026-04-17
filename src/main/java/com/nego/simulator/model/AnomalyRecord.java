package com.nego.simulator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyRecord {
    private AnomalyType type;
    private String description;
    private Integer round; // 发生异常时的轮次
    private Double relevantPrice; // 相关的异常报价
    private String negotiationId; // 追踪是哪场谈判发生的异常
    private String timestamp; // 发生异常的时间戳
}
