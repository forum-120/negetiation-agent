package com.nego.simulator.controller;

import com.nego.simulator.model.AgentCallRequest;
import com.nego.simulator.model.AgentCallResponse;
import com.nego.simulator.model.AgentInitRequest;
import com.nego.simulator.model.AgentInitResponse;
import com.nego.simulator.service.BuyerAgentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/agent/buyer")
@ConditionalOnProperty(name = "nego.role", havingValue = "buyer")
public class BuyerAgentController {

    private final BuyerAgentService buyerAgentService;

    public BuyerAgentController(BuyerAgentService buyerAgentService) {
        this.buyerAgentService = buyerAgentService;
    }

    @PostMapping("/init")
    public ResponseEntity<AgentInitResponse> init(@RequestBody AgentInitRequest request) {
        return ResponseEntity.ok(buyerAgentService.init(request));
    }

    @PostMapping("/call")
    public ResponseEntity<AgentCallResponse> call(@RequestBody AgentCallRequest request) {
        return ResponseEntity.ok(buyerAgentService.call(request));
    }

    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<Void> destroySession(@PathVariable String sessionId) {
        buyerAgentService.destroySession(sessionId);
        return ResponseEntity.noContent().build();
    }
}
