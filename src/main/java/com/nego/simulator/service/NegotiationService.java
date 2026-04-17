package com.nego.simulator.service;

import java.util.*;
import java.util.stream.Collectors;

import com.nego.simulator.config.TransportConfig;
import com.nego.simulator.model.*;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "nego.role", havingValue = "orchestrator", matchIfMissing = true)
public class NegotiationService {

    private final RedisProductRepository productRepository;
    private final RedisHistoryRepository historyRepository;
    private final NegotiationOrchestrator orchestrator;
    private final TransportConfig.TransportFactory transportFactory;
    private final Tracer tracer;

    public NegotiationService(RedisProductRepository productRepository,
                               RedisHistoryRepository historyRepository,
                               NegotiationOrchestrator orchestrator,
                               TransportConfig.TransportFactory transportFactory,
                               Tracer tracer) {
        this.productRepository = productRepository;
        this.historyRepository = historyRepository;
        this.orchestrator = orchestrator;
        this.transportFactory = transportFactory;
        this.tracer = tracer;
    }

    // ── 查询接口 ──────────────────────────────────────────

    public List<ServiceInfo> getServices() {
        return productRepository.findAll();
    }

    public List<NegotiationResult> getHistory() {
        return historyRepository.findAllResults();
    }

    public List<AnomalyRecord> getAllAnomalies() {
        return historyRepository.findAllAnomalies();
    }

    // ── 核心谈判入口 ──────────────────────────────────────

    /**
     * 执行单次谈判并持久化结果。
     *
     * @param serviceId 商品 ID
     * @param config    谈判配置（策略 / 轮数 / 阈值 / RAG 模式）
     * @return 谈判结果
     */
    public NegotiationResult negotiate(String serviceId, NegotiationConfig config) {
        // root span：覆盖整个 negotiate() 调用
        Span sessionSpan = tracer.spanBuilder("negotiation.session")
                .setAttribute("service.id", serviceId)
                .setAttribute("rag.mode", config.getRagMode() != null ? config.getRagMode().name() : "NONE")
                .setAttribute("buyer.strategy", config.getBuyerStrategy().name())
                .setAttribute("seller.strategy", config.getSellerStrategy().name())
                .startSpan();

        try (Scope ignored = sessionSpan.makeCurrent()) {

            ServiceInfo service = productRepository.findById(serviceId)
                    .orElseThrow(() -> new RuntimeException("商品不存在：" + serviceId));

            int maxRounds = config.getMaxRounds() != null ? config.getMaxRounds() : 8;
            double threshold = config.getConvergenceThreshold() != null ? config.getConvergenceThreshold() : 0.03;

            // 创建本场谈判独享的 Transport（InProcess 或 Http，由 TransportFactory 决定）
            try (AgentTransport transport = transportFactory.createTransport(
                    config.getBuyerStrategy(), config.getSellerStrategy(), service.getAskingPrice())) {

                // 调用 Orchestrator 执行谈判循环
                List<AnomalyRecord> anomalies = new ArrayList<>();
                NegotiationResult result = orchestrator.run(
                        service, config, transport, maxRounds, threshold, anomalies);

                // 收集 Tools 层的约束违规记录
                anomalies.addAll(transport.getBuyerViolations());
                anomalies.addAll(transport.getSellerViolations());
                result.setAnomalyRecords(anomalies);

                // 持久化
                String negotiationId = UUID.randomUUID().toString();
                sessionSpan.setAttribute("negotiation.id", negotiationId);
                sessionSpan.setAttribute("negotiation.agreed", Boolean.TRUE.equals(result.getAgreed()));
                sessionSpan.setAttribute("negotiation.rounds", result.getRounds());

                String now = java.time.LocalDateTime.now().toString();
                for (AnomalyRecord r : anomalies) {
                    r.setNegotiationId(negotiationId);
                    r.setTimestamp(now);
                }
                historyRepository.saveAnomalies(anomalies);
                historyRepository.saveResult(result);
                historyRepository.saveMessages(negotiationId, result.getMessages());

                if (Boolean.TRUE.equals(result.getAgreed())) {
                    System.out.println("[结算] negotiationId=" + negotiationId
                            + " price=" + result.getFinalPrice());
                }
                return result;
            }

        } catch (Exception e) {
            sessionSpan.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            sessionSpan.end();
        }
    }

    // ── 批量跑批 ──────────────────────────────────────────

    /**
     * 批量跑 48 组 4 维实验矩阵（2×2×4×3）。
     */
    public List<NegotiationResult> batchCompare() {
        List<NegotiationResult> results = new ArrayList<>();
        List<BuyerStrategy> buyerStrategies = Arrays.asList(BuyerStrategy.AGGRESSIVE, BuyerStrategy.CONSERVATIVE);
        List<SellerStrategy> sellerStrategies = Arrays.asList(SellerStrategy.PREMIUM, SellerStrategy.FLEXIBLE);
        List<RagMode> ragModes = Arrays.asList(RagMode.values());
        List<String> serviceIds = Arrays.asList("svc-001", "svc-003", "svc-007");

        for (String svcId : serviceIds) {
            for (BuyerStrategy bs : buyerStrategies) {
                for (SellerStrategy ss : sellerStrategies) {
                    for (RagMode rag : ragModes) {
                        NegotiationConfig config = NegotiationConfig.builder()
                                .buyerStrategy(bs).sellerStrategy(ss).ragMode(rag)
                                .maxRounds(8).convergenceThreshold(0.03).build();
                        try {
                            results.add(negotiate(svcId, config));
                        } catch (Exception e) {
                            System.err.println("❌ 批量跑批异常，跳过：" + bs + "/" + ss + "/" + rag + "/" + svcId);
                            System.err.println("原因: " + e.getMessage());
                        }
                    }
                }
            }
        }
        return results;
    }

    // ── 统计分析 ──────────────────────────────────────────

    public Map<String, Map<String, Object>> getAnalytics() {
        return historyRepository.findAllResults().stream()
                .collect(Collectors.groupingBy(
                        r -> r.getBuyerStrategy() + " × " + r.getSellerStrategy(),
                        Collectors.collectingAndThen(Collectors.toList(), list -> {
                            Map<String, Object> stats = new HashMap<>();
                            long total = list.size();
                            long agreed = list.stream().filter(r -> Boolean.TRUE.equals(r.getAgreed())).count();
                            stats.put("totalRuns", total);
                            stats.put("dealRate", total > 0 ? (double) agreed / total : 0);
                            stats.put("avgDiscount", list.stream()
                                    .filter(r -> r.getDiscount() != null)
                                    .mapToDouble(NegotiationResult::getDiscount).average().orElse(0));
                            stats.put("avgRounds", list.stream()
                                    .mapToInt(NegotiationResult::getRounds).average().orElse(0));
                            return stats;
                        })));
    }
}
