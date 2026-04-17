package com.nego.simulator.service;

import com.nego.simulator.agent.SellerAgent;
import com.nego.simulator.agent.SellerTools;
import com.nego.simulator.model.AgentCallRequest;
import com.nego.simulator.model.AgentCallResponse;
import com.nego.simulator.model.AgentInitRequest;
import com.nego.simulator.model.AgentInitResponse;
import com.nego.simulator.model.SellerStrategy;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(name = "nego.role", havingValue = "seller")
public class SellerAgentService {

    private final ChatLanguageModel chatLanguageModel;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public SellerAgentService(ChatLanguageModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
    }

    public AgentInitResponse init(AgentInitRequest request) {
        SellerStrategy strategy = SellerStrategy.valueOf(request.getStrategy());
        SellerTools tools = new SellerTools(strategy, request.getAskingPrice());
        SellerAgent agent = AiServices.builder(SellerAgent.class)
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
            throw new RuntimeException("Seller session not found: " + request.getSessionId());
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
        final SellerAgent agent;
        final SellerTools tools;

        Session(SellerAgent agent, SellerTools tools) {
            this.agent = agent;
            this.tools = tools;
        }
    }
}
