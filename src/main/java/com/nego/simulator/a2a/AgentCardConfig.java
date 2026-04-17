package com.nego.simulator.a2a;

import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentProvider;
import io.a2a.spec.AgentSkill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AgentCardConfig {

    @Bean
    @ConditionalOnProperty(name = "nego.role", havingValue = "buyer")
    public AgentCard buyerAgentCard(@Value("${server.port:8081}") int port) {
        return new AgentCard.Builder()
                .name("NegotiationBuyerAgent")
                .description("LLM-powered buyer agent for iterative price negotiation")
                .url("http://buyer-agent:" + port)
                .provider(new AgentProvider("nego-simulator", "https://github.com/nego-simulator"))
                .version("1.0.0")
                .capabilities(new AgentCapabilities(false, false, false, List.of()))
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of(new AgentSkill(
                        "negotiate_price", "Price Negotiation",
                        "Makes counter-offers using buyer strategy and optional RAG context",
                        List.of("negotiation", "pricing"), List.of(), List.of(), List.of(), List.of())))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "nego.role", havingValue = "seller")
    public AgentCard sellerAgentCard(@Value("${server.port:8082}") int port) {
        return new AgentCard.Builder()
                .name("NegotiationSellerAgent")
                .description("LLM-powered seller agent for iterative price negotiation")
                .url("http://seller-agent:" + port)
                .provider(new AgentProvider("nego-simulator", "https://github.com/nego-simulator"))
                .version("1.0.0")
                .capabilities(new AgentCapabilities(false, false, false, List.of()))
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of(new AgentSkill(
                        "negotiate_price", "Price Negotiation",
                        "Makes counter-offers using seller strategy and optional RAG context",
                        List.of("negotiation", "pricing"), List.of(), List.of(), List.of(), List.of())))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "nego.role", havingValue = "orchestrator", matchIfMissing = true)
    public AgentCard orchestratorAgentCard(@Value("${server.port:8080}") int port) {
        return new AgentCard.Builder()
                .name("NegotiationOrchestrator")
                .description("Orchestrates multi-round price negotiation between buyer and seller agents via A2A protocol")
                .url("http://orchestrator:" + port)
                .provider(new AgentProvider("nego-simulator", "https://github.com/nego-simulator"))
                .version("1.0.0")
                .capabilities(new AgentCapabilities(false, false, false, List.of()))
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of(new AgentSkill(
                        "orchestrate_negotiation", "Negotiation Orchestration",
                        "Coordinates buyer-seller negotiation sessions with configurable strategies",
                        List.of("orchestration", "negotiation"), List.of(), List.of(), List.of(), List.of())))
                .build();
    }
}
