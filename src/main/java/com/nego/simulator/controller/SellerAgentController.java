package com.nego.simulator.controller;

import com.nego.simulator.model.AgentCallRequest;
import com.nego.simulator.model.AgentCallResponse;
import com.nego.simulator.model.AgentInitRequest;
import com.nego.simulator.model.AgentInitResponse;
import com.nego.simulator.service.SellerAgentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/agent/seller")
@ConditionalOnProperty(name = "nego.role", havingValue = "seller")
public class SellerAgentController {

    private final SellerAgentService sellerAgentService;

    public SellerAgentController(SellerAgentService sellerAgentService) {
        this.sellerAgentService = sellerAgentService;
    }

    @PostMapping("/init")
    public ResponseEntity<AgentInitResponse> init(@RequestBody AgentInitRequest request) {
        return ResponseEntity.ok(sellerAgentService.init(request));
    }

    @PostMapping("/call")
    public ResponseEntity<AgentCallResponse> call(@RequestBody AgentCallRequest request) {
        return ResponseEntity.ok(sellerAgentService.call(request));
    }

    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<Void> destroySession(@PathVariable String sessionId) {
        sellerAgentService.destroySession(sessionId);
        return ResponseEntity.noContent().build();
    }
}
