package com.nego.simulator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentCallResponse {

    /**
     * 服务端当前实际使用的 sessionId。
     *
     * <p>远程 transport 可用它修正本地缓存的 sessionId，
     * 确保多轮谈判持续命中同一个 agent 会话。</p>
     */
    private String sessionId;

    private String response;

    private double lastOffer;

    private boolean accepted;

    private List<AnomalyRecord> violations;
}
