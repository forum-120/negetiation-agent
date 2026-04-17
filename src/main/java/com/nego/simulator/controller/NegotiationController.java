package com.nego.simulator.controller;


import com.nego.simulator.model.*;
import com.nego.simulator.service.NegotiationService;
import io.a2a.spec.AgentCard;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
@ConditionalOnProperty(name = "nego.role", havingValue = "orchestrator", matchIfMissing = true)
public class NegotiationController {


    private final NegotiationService negotiationService;
    private final AgentCard agentCard;

    public NegotiationController(NegotiationService negotiationService, AgentCard agentCard) {
        this.negotiationService = negotiationService;
        this.agentCard = agentCard;
    }

    @GetMapping("/.well-known/agent-card.json")
    public AgentCard getOrchestratorCard() {
        return agentCard;
    }

    @GetMapping("/a2a/card")
    public AgentCard getOrchestratorCardAlt() {
        return agentCard;
    }

    //获取商品列表
    @GetMapping("/services")
    public List<ServiceInfo> getServices() {
        return negotiationService.getServices();
    }

    //获取可选策略列表
    @GetMapping("/strategies")
    public Map<String, Object> getStrategies() {
        Map<String, Object> result = new HashMap<>();
        result.put("buyerStrategies", BuyerStrategy.values());
        result.put("sellerStrategies", SellerStrategy.values());
        return result;
    }

    /**
     * 发起单次谈判
     */
    @PostMapping("/negotiate")
    public ResponseEntity<NegotiationResult> negotiate(@RequestBody NegotiateRequest request) {
        NegotiationConfig config = NegotiationConfig.builder()
                .buyerStrategy(request.buyerStrategy)
                .sellerStrategy(request.sellerStrategy)
                .ragMode(request.ragMode != null ? request.ragMode : RagMode.NONE)
                .maxRounds(request.maxRounds)
                .convergenceThreshold(request.convergenceThreshold)
                .build();

        NegotiationResult result = negotiationService.negotiate(request.serviceId, config);

        //成交返回200，未成交返回402
        if(Boolean.TRUE.equals(result.getAgreed())) {
            return ResponseEntity.ok()
                    .header("X-Payment-Status", "agreed")
                    .body(result);
        }else {
            return ResponseEntity.status(402)
                    .header("X-Payment-Status", "rejected")
                    .body(result);
        }
    }

    /**
     * 批量跑 48 组 4 维策略/RAG组合
     */
    @PostMapping("/negotiate/batch")
    public List<NegotiationResult> batchCompare() {
        return negotiationService.batchCompare();
    }

    //获取历史记录
    @GetMapping("/history")
    public List<NegotiationResult> getHistory() {
        return negotiationService.getHistory();
    }

    //获取统计分析
    @GetMapping("/analytics")
    public Map<String, Map<String, Object>> getAnalytics() {
        return negotiationService.getAnalytics();
    }


    /**
     * 获取所有的全局异常行为数据，用于大盘数据展示
     */
    @GetMapping("/negotiation/anomalies")
    public ResponseEntity<List<AnomalyRecord>> getAllAnomalies() {
        return ResponseEntity.ok(negotiationService.getAllAnomalies());
    }

    /**
     * 请求体内部类
     */


    //单次谈判请求
    static class NegotiateRequest {
        public String serviceId;
        public BuyerStrategy buyerStrategy;
        public SellerStrategy sellerStrategy;
        public RagMode ragMode;
        public Integer maxRounds;
        // 使用 Double（装箱类型）而非 double（基本类型）：
        // 当 JSON 不传此字段时，Jackson 会将其反序列化为 null，
        // NegotiationService 才能正确走默认值 0.03。
        // 若用 double，不传时默认 0.0，收敛检查实际变成"价格完全相等才收敛"。
        public Double convergenceThreshold;
    }




}
