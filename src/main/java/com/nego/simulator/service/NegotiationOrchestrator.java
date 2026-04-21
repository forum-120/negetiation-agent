package com.nego.simulator.service;

import com.nego.simulator.model.*;
import com.nego.simulator.protocol.ProtocolExecutor;
import com.nego.simulator.protocol.ProtocolExecutorFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "nego.role", havingValue = "orchestrator", matchIfMissing = true)
public class NegotiationOrchestrator {

    private final ProtocolExecutorFactory executorFactory;

    public NegotiationOrchestrator(ProtocolExecutorFactory executorFactory) {
        this.executorFactory = executorFactory;
    }

    public NegotiationResult run(ServiceInfo service, NegotiationConfig config,
                                  AgentTransport transport,
                                  int maxRounds, double threshold,
                                  List<AnomalyRecord> anomalies,
                                  String negotiationId, int trialIndex) {
        if (config.getProtocol() == null) config.setProtocol(NegotiateProtocol.ITERATIVE_BARGAIN);
        if (config.getMaxRounds() == null) config.setMaxRounds(maxRounds);
        if (config.getConvergenceThreshold() == null) config.setConvergenceThreshold(threshold);

        ProtocolExecutor executor = executorFactory.get(config.getProtocol());
        return executor.execute(service, config, transport, anomalies, negotiationId, trialIndex);
    }
}
