package com.nego.simulator.service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import com.nego.simulator.agent.BuyerAgent;
import com.nego.simulator.agent.BuyerTools;
import com.nego.simulator.agent.SellerAgent;
import com.nego.simulator.agent.SellerTools;
import com.nego.simulator.model.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.springframework.stereotype.Service;

@Service
public class NegotiationService {

    private final ChatLanguageModel chatLanguageModel;
    private final List<NegotiationResult> history = new ArrayList<>();

    public NegotiationService(ChatLanguageModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
    }

    /** 硬编码的商品列表，模拟数据源 */
    private final List<ServiceInfo> services = List.of(
            ServiceInfo.builder()
                    .id("svc-001")
                    .name("AI 数据标注服务")
                    .description("提供高质量的 NLP 数据标注，支持命名实体识别和情感分析")
                    .askingPrice(150.0)
                    .category("数据服务")
                    .build(),
            ServiceInfo.builder()
                    .id("svc-002")
                    .name("云端 GPU 算力租赁")
                    .description("A100 GPU 按小时租赁，适合模型训练和推理任务")
                    .askingPrice(200.0)
                    .category("算力服务")
                    .build(),
            ServiceInfo.builder()
                    .id("svc-003")
                    .name("智能客服 API")
                    .description("基于大模型的智能客服接口，支持多轮对话和意图识别")
                    .askingPrice(100.0)
                    .category("API 服务")
                    .build()
    );

    //获取商品列表
    public List<ServiceInfo> getServices() {
        return services;
    }

    //获取历史记录
    public List<NegotiationResult> getHistory() {
        return history;
    }

    /**
     * 执行单次谈判
     *
     * @param serviceId 商品Id
     * @param config 谈判配置（策略，轮数，阈值）
     * @return 谈判结果
     */
    public NegotiationResult negotiate(String serviceId, NegotiationConfig config) {
        //1.查找商品
        ServiceInfo service = services.stream()
                .filter(s->s.getId().equals(serviceId))
                .findFirst()
            .orElseThrow(() -> new RuntimeException("商品不存在：" + serviceId));

        //2.填充默认值
        int maxRounds = config.getMaxRounds() != null ? config.getMaxRounds():5;
        double threshold = config.getConvergenceThreshold() !=null ? config.getConvergenceThreshold() : 0.05;

        //3.初始化有状态的tools
        BuyerTools buyerTools = new BuyerTools(config.getBuyerStrategy(), service.getAskingPrice());
        SellerTools sellerTools = new SellerTools(config.getSellerStrategy(), service.getAskingPrice());

        //4.动态创建Agent（每次谈判全新实例，避免状态污染）
        BuyerAgent buyerAgent = AiServices.builder(BuyerAgent.class)
                .chatLanguageModel(chatLanguageModel)
                .tools(buyerTools)
                .build();

        SellerAgent sellerAgent = AiServices.builder(SellerAgent.class)
                .chatLanguageModel(chatLanguageModel)
                .tools(sellerTools)
                .build();

        //5.执行多轮谈判
        NegotiationResult result = runNegotiationLoop(
                service, config, buyerAgent, sellerAgent,
                buyerTools, sellerTools, maxRounds, threshold
        );

        //6.保存历史
        history.add(result);

        //7.如果成交，打印结算日志
        if(Boolean.TRUE.equals(result.getAgreed())) {
            logSettlement(result, service);
        }
        return result;
    }

    /**
     * 核心
     * 多轮谈判循环
     */
    private NegotiationResult runNegotiationLoop (
            ServiceInfo service, NegotiationConfig config,
            BuyerAgent buyerAgent, SellerAgent sellerAgent,
            BuyerTools buyerTools, SellerTools sellerTools,
            int maxRounds, double threshold) {

        List<NegotiationMessage> messages = new ArrayList<>();
        double buyerPrice = 0;
        double sellerPrice = service.getAskingPrice();

        for(int round=1;round<=maxRounds;round++) {

            //买方回合
            String buyerContext = buildBuyerContext(service, config.getBuyerStrategy(),
                    round, maxRounds, buyerPrice, sellerPrice);
            String buyerResponse = buyerAgent.negotiate(buyerContext);
            buyerPrice = buyerTools.getLastOffer();

            messages.add(NegotiationMessage.builder()
                    .role("BUYER")
                    .content(buyerResponse)
                    .price(buyerPrice)
                    .round(round)
                    .timestamp(LocalDateTime.now())
                    .build()
            );

            //买方选择接受
            if(buyerTools.isAccepted()){
                return buildResult(true, buyerPrice, service, messages, round ,config);
            }

            // —— 卖方回合 ——
            String sellerContext = buildSellerContext(service, config.getSellerStrategy(),
                    round, maxRounds, sellerPrice, buyerPrice);
            String sellerResponse = sellerAgent.negotiate(sellerContext);
            sellerPrice = sellerTools.getLastOffer();
            messages.add(NegotiationMessage.builder()
                    .role("SELLER")
                    .content(sellerResponse)
                    .price(sellerPrice)
                    .round(round)
                    .timestamp(LocalDateTime.now())
                    .build());
            // 卖方选择接受
            if (sellerTools.isAccepted()) {
                return buildResult(true, sellerPrice, service, messages, round, config);
            }

            //收敛判断，双方价格接近按照中间价成交
            if(isConverged(buyerPrice, sellerPrice, threshold)) {
                double midPrice = (buyerPrice+sellerPrice)/2;
                return buildResult(true, midPrice, service, messages, round, config);
            }
        }

        //超过最大轮数，谈判失败
        return buildResult(false, 0, service, messages, maxRounds, config);
    }

    /**
     * 构建买方每轮上下文
     */
    private String buildBuyerContext(ServiceInfo service, BuyerStrategy strategy,
                                     int round, int maxRounds, double myLastOffer, double opponentLastOffer) {

        String res = String.format("""
                [谈判状态]
                商品：%s
                商品描述：%s
                原始要价：$%.2f
                当前轮次：第 %d 轮 / 共 %d 轮
                你的策略：%s（首轮%.0f%%，每轮加%.0f%%-%.0f%%，预算上限%.0f%%）
                你上一轮出价：%s
                对方上一轮出价：%s
                请做出你的下一步谈判动作。
                """,
                service.getName(),
                service.getDescription(),
                service.getAskingPrice(),
                round,maxRounds,
                strategy.getDescription(),
                strategy.getOpenRatio()*100,
                strategy.getMinStep()*100,
                strategy.getMaxStep()*100,
                strategy.getCeiling()*100,
                myLastOffer > 0 ? String.format("$%.2f", myLastOffer) : "尚未出价",
                opponentLastOffer > 0 ? String.format("$%.2f", opponentLastOffer) : "尚未出价"
        );

        return res;
    }


    /**
     * 构建卖方每轮上下文
     */
    private String buildSellerContext(ServiceInfo service, SellerStrategy strategy,
                                      int round, int maxRounds,
                                      double myLastOffer, double opponentLastOffer) {
        return String.format("""
                [谈判状态]
                商品：%s
                商品描述：%s
                原始要价：$%.2f
                当前轮次：第 %d 轮 / 共 %d 轮
                你的策略：%s（首轮%.0f%%，每轮降%.0f%%-%.0f%%，底线%.0f%%）
                你上一轮报价：%s
                对方上一轮出价：%s
                请做出你的下一步谈判动作。
                """,
                service.getName(),
                service.getDescription(),
                service.getAskingPrice(),
                round, maxRounds,
                strategy.getDescription(),
                strategy.getOpenRatio() * 100,
                strategy.getMinStep() * 100,
                strategy.getMaxStep() * 100,
                strategy.getFloor() * 100,
                myLastOffer > 0 ? String.format("$%.2f", myLastOffer) : "尚未报价",
                opponentLastOffer > 0 ? String.format("$%.2f", opponentLastOffer) : "尚未出价"
        );
    }


    /**
     * 判断收敛
     */
    private boolean isConverged(double buyerPrice, double sellerPrice, double threshold) {
        if(buyerPrice <=0 || sellerPrice <=0){
            return false;
        }
        double gap = Math.abs(sellerPrice-buyerPrice) /sellerPrice;
        return gap <= threshold;
    }

    /**
     * 构建谈判结果
     */
    private NegotiationResult buildResult(boolean agreed, double finalPrice, ServiceInfo service, List<NegotiationMessage> messages, int rounds, NegotiationConfig config) {

        double originalPrice = service.getAskingPrice();
        double discount = agreed ? (originalPrice-finalPrice)/originalPrice : 0;

        return NegotiationResult.builder()
                .agreed(agreed)
                .finalPrice(agreed ? finalPrice:null)
                .originalPrice(originalPrice)
                .discount(agreed? discount:null)
                .rounds(rounds)
                .messages(messages)
                .buyerSatisfaction(agreed ? calculateSatisfaction(finalPrice,originalPrice,true) : null)
                .sellerSatisfaction(agreed ? calculateSatisfaction(finalPrice,originalPrice,false) : null)
                .buyerStrategy(config.getBuyerStrategy().name())
                .sellerStrategy(config.getSellerStrategy().name())
                .build();
    }

    /**
     * 计算满意度。
     * 买方：成交价越低越满意；卖方：成交价越高越满意。
     */
    private double calculateSatisfaction(double finalPrice, double originalPrice, boolean isBuyer) {
        double ratio = finalPrice / originalPrice;
        if (isBuyer) {
            // 买方：折扣越大越满意，ratio 越小满意度越高
            return Math.max(0, Math.min(1, 1 - ratio + 0.2));
        } else {
            // 卖方：价格越接近原价越满意
            return Math.max(0, Math.min(1, ratio));
        }
    }

    /**
     * 打印模拟结算日志。
     */
    private void logSettlement(NegotiationResult result, ServiceInfo service) {
        SettlementRecord record = SettlementRecord.builder()
                .negotiationId(UUID.randomUUID().toString())
                .serviceName(service.getName())
                .finalPrice(result.getFinalPrice())
                .status("SUCCESS")
                .timestamp(LocalDateTime.now())
                .build();
        System.out.println("[结算日志] " + record);
    }

    /**
     * 批量跑 4 种策略组合。
     */
    public List<NegotiationResult> batchCompare(String serviceId) {
        List<NegotiationResult> results = new ArrayList<>();
        for (BuyerStrategy bs : BuyerStrategy.values()) {
            for (SellerStrategy ss : SellerStrategy.values()) {
                NegotiationConfig config = NegotiationConfig.builder()
                        .buyerStrategy(bs)
                        .sellerStrategy(ss)
                        .maxRounds(5)
                        .convergenceThreshold(0.05)
                        .build();
                results.add(negotiate(serviceId, config));
            }
        }
        return results;
    }

    /**
     * 统计分析：按策略组合分组统计。
     */
    public Map<String, Map<String, Object>> getAnalytics() {
        return history.stream()
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
                                    .mapToDouble(NegotiationResult::getDiscount)
                                    .average().orElse(0));
                            stats.put("avgRounds", list.stream()
                                    .mapToInt(NegotiationResult::getRounds)
                                    .average().orElse(0));
                            return stats;
                        })
                ));
    }




}


