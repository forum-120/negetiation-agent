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

    private String response;

    private double lastOffer;

    private boolean accepted;

    private List<AnomalyRecord> violations;
}
