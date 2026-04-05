package com.nego.simulator.controller;


import com.nego.simulator.model.*;
import com.nego.simulator.service.NegotiationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 谈判控制器
 *
 * 暴露REST API, 供前端调用发起谈判，查询结果
 * 成交返回200，未成交返回402
 */
@RestController
@RequestMapping("/api")
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
     * 批量跑4种策略组合
     */
    @PostMapping("/negotiate/batch")
    public List<NegotiationResult> batchCompare(@RequestBody BatchRequest request) {
        return negotiationService.batchCompare((request.serviceId));
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
     * 请求体内部类
     */


    //单次谈判请求
    static class NegotiateRequest {
        public String serviceId;
        public BuyerStrategy buyerStrategy;
        public SellerStrategy sellerStrategy;
        public Integer maxRounds;
        public double convergenceThreshold;
    }

    //批量对比请求体
    static class BatchRequest {
        public String serviceId;
    }




}
