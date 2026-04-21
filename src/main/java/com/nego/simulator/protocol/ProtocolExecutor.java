package com.nego.simulator.protocol;

import com.nego.simulator.model.AnomalyRecord;
import com.nego.simulator.model.NegotiationConfig;
import com.nego.simulator.model.NegotiationResult;
import com.nego.simulator.model.ServiceInfo;
import com.nego.simulator.service.AgentTransport;

import java.util.List;

public interface ProtocolExecutor {

    NegotiationResult execute(ServiceInfo service, NegotiationConfig config,
                              AgentTransport transport, List<AnomalyRecord> anomalies,
                              String negotiationId, int trialIndex);
}
