package com.nego.simulator.controller;


import com.nego.simulator.model.*;
import com.nego.simulator.model.NegotiateProtocol;
import com.nego.simulator.service.NegotiationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
@ConditionalOnProperty(name = "nego.role", havingValue = "orchestrator", matchIfMissing = true)
public class NegotiationController {


    private final NegotiationService negotiationService;

    public NegotiationController(NegotiationService negotiationService) {
        this.negotiationService = negotiationService;
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
                .protocol(request.protocol != null
                        ? NegotiateProtocol.valueOf(request.protocol.toUpperCase())
                        : NegotiateProtocol.ITERATIVE_BARGAIN)
                .maxRounds(request.maxRounds)
                .convergenceThreshold(request.convergenceThreshold)
                .build();

        NegotiationResult result = negotiationService.negotiate(request.serviceId, config);

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

    @PostMapping("/batch/{protocol}")
    public List<NegotiationResult> batchByProtocol(@PathVariable String protocol) {
        NegotiateProtocol proto = NegotiateProtocol.valueOf(protocol.toUpperCase());
        return negotiationService.batchByProtocol(proto);
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
        public Double convergenceThreshold;
        public String protocol;
    }




}
