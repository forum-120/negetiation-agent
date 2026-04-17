package com.nego.simulator.service;

import com.nego.simulator.agent.BuyerAgent;
import com.nego.simulator.agent.BuyerTools;
import com.nego.simulator.agent.SellerAgent;
import com.nego.simulator.agent.SellerTools;
import com.nego.simulator.model.AnomalyRecord;
import com.nego.simulator.model.BuyerStrategy;
import com.nego.simulator.model.SellerStrategy;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;

import java.util.List;

/**
 * InProcessTransport — AgentTransport 的 JVM 内存实现。
 *
 * <p>
 * 持有 BuyerAgent / SellerAgent 以及对应的有状态 BuyerTools / SellerTools。
 * 调用 sendToBuyer / sendToSeller 时直接在同一进程中调用 LLM，与原来
 * {@code NegotiationService.runNegotiationLoop} 的内联调用行为完全等价。
 * </p>
 *
 * <p>Phase 1 时此类不变，Orchestrator 只需换用 {@code A2aHttpTransport}。</p>
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
    public String sendToBuyer(String context) {
        return buyerAgent.negotiate(context);
    }

    @Override
    public String sendToSeller(String context) {
        return sellerAgent.negotiate(context);
    }

    @Override
    public boolean isBuyerAccepted() {
        return buyerTools.isAccepted();
    }

    @Override
    public boolean isSellerAccepted() {
        return sellerTools.isAccepted();
    }

    @Override
    public double getBuyerLastOffer() {
        return buyerTools.getLastOffer();
    }

    @Override
    public double getSellerLastOffer() {
        return sellerTools.getLastOffer();
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
    }

    public BuyerTools getBuyerTools() {
        return buyerTools;
    }

    public SellerTools getSellerTools() {
        return sellerTools;
    }
}
