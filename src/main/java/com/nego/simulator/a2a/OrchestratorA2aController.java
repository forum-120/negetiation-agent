package com.nego.simulator.a2a;

import io.a2a.spec.AgentCard;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "nego.role", havingValue = "orchestrator", matchIfMissing = true)
public class OrchestratorA2aController {

    private final AgentCard agentCard;

    public OrchestratorA2aController(AgentCard agentCard) {
        this.agentCard = agentCard;
    }

    @GetMapping("/.well-known/agent-card.json")
    public AgentCard getCard() {
        return agentCard;
    }

    @GetMapping("/a2a/card")
    public AgentCard getCardAlt() {
        return agentCard;
    }
}
