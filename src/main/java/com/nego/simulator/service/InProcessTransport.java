package com.nego.simulator.service;

import com.nego.simulator.agent.BuyerAgent;
import com.nego.simulator.agent.BuyerTools;
import com.nego.simulator.agent.SellerAgent;
import com.nego.simulator.agent.SellerTools;
import com.nego.simulator.model.AnomalyRecord;
import com.nego.simulator.model.BuyerStrategy;
import com.nego.simulator.model.OfferResponse;
import com.nego.simulator.model.SellerStrategy;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * InProcessTransport — AgentTransport 的 JVM 内存实现。
 *
 * <p>持有 BuyerAgent / SellerAgent 以及对应的有状态 BuyerTools / SellerTools。
 * 调用时直接在同一进程中调用 LLM，与原 NegotiationService 内联调用行为完全等价。</p>
 *
 * <p>返回 {@code CompletableFuture.completedFuture()} 满足接口契约，
 * 同步场景下无额外开销；Phase 2 并发调用时可直接换用异步实现。</p>
 *
 * <p>Phase 1 时此类不变，Orchestrator 只需换用 A2aHttpTransport。</p>
 */
public class InProcessTransport implements AgentTransport {

    private final BuyerAgent buyerAgent;
    private final SellerAgent sellerAgent;
    private final BuyerTools buyerTools;
    private final SellerTools sellerTools;

    public InProcessTransport(ChatLanguageModel llm,
                               BuyerStrategy buyerStrategy,
                               SellerStrategy sellerStrategy,
                               double askingPrice) {
        this.buyerTools = new BuyerTools(buyerStrategy, askingPrice);
        this.sellerTools = new SellerTools(sellerStrategy, askingPrice);

        this.buyerAgent = AiServices.builder(BuyerAgent.class)
                .chatLanguageModel(llm)
                .tools(buyerTools)
                .build();

        this.sellerAgent = AiServices.builder(SellerAgent.class)
                .chatLanguageModel(llm)
                .tools(sellerTools)
                .build();
    }

    @Override
    public CompletableFuture<OfferResponse> sendToBuyer(String context) {
        String text = buyerAgent.negotiate(context);
        return CompletableFuture.completedFuture(OfferResponse.builder()
                .text(text)
                .lastOffer(buyerTools.getLastOffer())
                .accepted(buyerTools.isAccepted())
                .violations(buyerTools.getViolations())
                .build());
    }

    @Override
    public CompletableFuture<OfferResponse> sendToSeller(String context) {
        String text = sellerAgent.negotiate(context);
        return CompletableFuture.completedFuture(OfferResponse.builder()
                .text(text)
                .lastOffer(sellerTools.getLastOffer())
                .accepted(sellerTools.isAccepted())
                .violations(sellerTools.getViolations())
                .build());
    }

    @Override
    public void setOpponentOfferForBuyer(double sellerPrice) {
        buyerTools.setOpponentLastOffer(sellerPrice);
    }

    @Override
    public void setOpponentOfferForSeller(double buyerPrice) {
        sellerTools.setOpponentLastOffer(buyerPrice);
    }

    @Override
    public List<AnomalyRecord> getBuyerViolations() {
        return buyerTools.getViolations();
    }

    @Override
    public List<AnomalyRecord> getSellerViolations() {
        return sellerTools.getViolations();
    }

    @Override
    public void close() {
        // 同进程无外部资源需要释放
    }

    public BuyerTools getBuyerTools() {
        return buyerTools;
    }

    public SellerTools getSellerTools() {
        return sellerTools;
    }
}
