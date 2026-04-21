package com.nego.simulator.a2a;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nego.simulator.model.AgentCallRequest;
import com.nego.simulator.model.AgentCallResponse;
import com.nego.simulator.model.AgentInitRequest;
import com.nego.simulator.model.AgentInitResponse;
import com.nego.simulator.model.OfferResponse;
import com.nego.simulator.model.SellerStrategy;
import com.nego.simulator.service.SellerAgentService;
import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.events.EventQueue;
import io.a2a.server.tasks.TaskUpdater;
import io.a2a.spec.JSONRPCError;
import io.a2a.spec.Message;
import io.a2a.spec.TextPart;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "nego.role", havingValue = "seller")
public class SellerAgentExecutor implements AgentExecutor {

    private final SellerAgentService sellerService;
    private final ObjectMapper objectMapper;

    public SellerAgentExecutor(SellerAgentService sellerService, ObjectMapper objectMapper) {
        this.sellerService = sellerService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void execute(RequestContext ctx, EventQueue eventQueue) throws JSONRPCError {
        TaskUpdater updater = new TaskUpdater(ctx, eventQueue);

        if (ctx.getTask() == null) {
            updater.submit();
        }
        updater.startWork();

        Message message = ctx.getMessage();
        Map<String, Object> meta = message.getMetadata();

        String sessionId = (String) meta.getOrDefault("sessionId", null);
        String context = extractText(message);
        double opponentOffer = meta.containsKey("opponentOffer")
                ? ((Number) meta.get("opponentOffer")).doubleValue() : 0.0;

        if (sessionId == null || !sellerService.hasSession(sessionId)) {
            String strategyName = (String) meta.getOrDefault("strategy", "FLEXIBLE");
            double askingPrice = meta.containsKey("askingPrice")
                    ? ((Number) meta.get("askingPrice")).doubleValue() : 100.0;

            AgentInitRequest initReq = AgentInitRequest.builder()
                    .strategy(strategyName)
                    .askingPrice(askingPrice)
                    .build();
            AgentInitResponse initResp = sellerService.init(initReq);
            sessionId = initResp.getSessionId();
        }

        AgentCallRequest callReq = AgentCallRequest.builder()
                .sessionId(sessionId)
                .context(context)
                .opponentLastOffer(opponentOffer)
                .build();
        AgentCallResponse callResp = sellerService.call(callReq);

        OfferResponse offer = OfferResponse.builder()
                .sessionId(callResp.getSessionId())
                .text(callResp.getResponse())
                .lastOffer(callResp.getLastOffer())
                .accepted(callResp.isAccepted())
                .violations(callResp.getViolations())
                .build();

        try {
            String offerJson = objectMapper.writeValueAsString(offer);
            updater.addArtifact(List.of(new TextPart(offerJson)));
        } catch (Exception e) {
            updater.fail();
            return;
        }

        updater.complete();
    }

    @Override
    public void cancel(RequestContext ctx, EventQueue eventQueue) throws JSONRPCError {
        TaskUpdater updater = new TaskUpdater(ctx, eventQueue);
        updater.cancel();
    }

    private String extractText(Message message) {
        return message.getParts().stream()
                .filter(p -> p instanceof TextPart)
                .map(p -> ((TextPart) p).getText())
                .findFirst().orElse("");
    }
}
