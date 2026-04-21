package com.nego.simulator.protocol;

import com.nego.simulator.model.NegotiateProtocol;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ProtocolExecutorFactory {

    private final Map<NegotiateProtocol, ProtocolExecutor> executors;

    public ProtocolExecutorFactory(List<ProtocolExecutor> executorList) {
        this.executors = executorList.stream()
                .collect(Collectors.toMap(
                        e -> e.getClass().getAnnotation(SupportsProtocol.class).value(),
                        Function.identity()));
    }

    public ProtocolExecutor get(NegotiateProtocol protocol) {
        ProtocolExecutor exec = executors.get(protocol);
        if (exec == null) throw new IllegalArgumentException("Unsupported protocol: " + protocol);
        return exec;
    }
}
