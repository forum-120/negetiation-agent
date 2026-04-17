package com.nego.simulator.service;

import com.nego.simulator.agent.BuyerAgent;
import com.nego.simulator.agent.BuyerTools;
import com.nego.simulator.model.AgentCallRequest;
import com.nego.simulator.model.AgentCallResponse;
import com.nego.simulator.model.AgentInitRequest;
import com.nego.simulator.model.AgentInitResponse;
import com.nego.simulator.model.BuyerStrategy;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(name = "nego.role", havingValue = "buyer")
public class BuyerAgentService {

    private final ChatLanguageModel chatLanguageModel;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public BuyerAgentService(ChatLanguageModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
    }

    public AgentInitResponse init(AgentInitRequest request) {
        BuyerStrategy strategy = BuyerStrategy.valueOf(request.getStrategy());
        BuyerTools tools = new BuyerTools(strategy, request.getAskingPrice());
        BuyerAgent agent = AiServices.builder(BuyerAgent.class)
                .chatLanguageModel(chatLanguageModel)
                .tools(tools)
                .build();
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, new Session(agent, tools));
        return AgentInitResponse.builder().sessionId(sessionId).build();
    }

    public AgentCallResponse call(AgentCallRequest request) {
        Session session = sessions.get(request.getSessionId());
        if (session == null) {
            throw new RuntimeException("Buyer session not found: " + request.getSessionId());
        }
        session.tools.setOpponentLastOffer(request.getOpponentLastOffer());
        String response = session.agent.negotiate(request.getContext());
        return AgentCallResponse.builder()
                .response(response)
                .lastOffer(session.tools.getLastOffer())
                .accepted(session.tools.isAccepted())
                .violations(session.tools.getViolations())
                .build();
    }

    public boolean hasSession(String sessionId) {
        return sessions.containsKey(sessionId);
    }

    public void destroySession(String sessionId) {
        sessions.remove(sessionId);
    }

    private static class Session {
        final BuyerAgent agent;
        final BuyerTools tools;

        Session(BuyerAgent agent, BuyerTools tools) {
            this.agent = agent;
            this.tools = tools;
        }
    }
}
