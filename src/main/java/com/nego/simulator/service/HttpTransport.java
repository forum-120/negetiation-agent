package com.nego.simulator.service;

import com.nego.simulator.model.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * HttpTransport — AgentTransport 的 HTTP 远程调用实现。
 *
 * <p>
 * 通过 REST 调用远程 Buyer / Seller Agent 服务，
 * 替代 InProcessTransport 的同进程直调。
 * Orchestrator 启动此 Transport 时，按 nego.agent.buyer.url / seller.url 配置连接远端。
 * </p>
 */
public class HttpTransport implements AgentTransport {

    private final RestTemplate restTemplate;
    private final String buyerBaseUrl;
    private final String sellerBaseUrl;
    private final String buyerSessionId;
    private final String sellerSessionId;

    private double buyerLastOffer;
    private double sellerLastOffer;
    private boolean buyerAccepted;
    private boolean sellerAccepted;
    private double opponentOfferForBuyer;
    private double opponentOfferForSeller;

    private final List<AnomalyRecord> buyerViolations = new ArrayList<>();
    private final List<AnomalyRecord> sellerViolations = new ArrayList<>();

    public HttpTransport(RestTemplate restTemplate,
                         String buyerBaseUrl,
                         String sellerBaseUrl,
                         BuyerStrategy buyerStrategy,
                         SellerStrategy sellerStrategy,
                         double askingPrice) {
        this.restTemplate = restTemplate;
        this.buyerBaseUrl = buyerBaseUrl;
        this.sellerBaseUrl = sellerBaseUrl;

        AgentInitRequest buyerInit = AgentInitRequest.builder()
                .strategy(buyerStrategy.name())
                .askingPrice(askingPrice)
                .build();
        ResponseEntity<AgentInitResponse> buyerResp = restTemplate.postForEntity(
                buyerBaseUrl + "/agent/buyer/init", buyerInit, AgentInitResponse.class);
        this.buyerSessionId = buyerResp.getBody().getSessionId();

        AgentInitRequest sellerInit = AgentInitRequest.builder()
                .strategy(sellerStrategy.name())
                .askingPrice(askingPrice)
                .build();
        ResponseEntity<AgentInitResponse> sellerResp = restTemplate.postForEntity(
                sellerBaseUrl + "/agent/seller/init", sellerInit, AgentInitResponse.class);
        this.sellerSessionId = sellerResp.getBody().getSessionId();
    }

    @Override
    public String sendToBuyer(String context) {
        AgentCallRequest request = AgentCallRequest.builder()
                .sessionId(buyerSessionId)
                .context(context)
                .opponentLastOffer(opponentOfferForBuyer)
                .build();
        ResponseEntity<AgentCallResponse> resp = restTemplate.postForEntity(
                buyerBaseUrl + "/agent/buyer/call", request, AgentCallResponse.class);
        AgentCallResponse body = resp.getBody();
        this.buyerLastOffer = body.getLastOffer();
        this.buyerAccepted = body.isAccepted();
        if (body.getViolations() != null) {
            this.buyerViolations.addAll(body.getViolations());
        }
        return body.getResponse();
    }

    @Override
    public String sendToSeller(String context) {
        AgentCallRequest request = AgentCallRequest.builder()
                .sessionId(sellerSessionId)
                .context(context)
                .opponentLastOffer(opponentOfferForSeller)
                .build();
        ResponseEntity<AgentCallResponse> resp = restTemplate.postForEntity(
                sellerBaseUrl + "/agent/seller/call", request, AgentCallResponse.class);
        AgentCallResponse body = resp.getBody();
        this.sellerLastOffer = body.getLastOffer();
        this.sellerAccepted = body.isAccepted();
        if (body.getViolations() != null) {
            this.sellerViolations.addAll(body.getViolations());
        }
        return body.getResponse();
    }

    @Override
    public boolean isBuyerAccepted() {
        return buyerAccepted;
    }

    @Override
    public boolean isSellerAccepted() {
        return sellerAccepted;
    }

    @Override
    public double getBuyerLastOffer() {
        return buyerLastOffer;
    }

    @Override
    public double getSellerLastOffer() {
        return sellerLastOffer;
    }

    @Override
    public void setOpponentOfferForBuyer(double sellerPrice) {
        this.opponentOfferForBuyer = sellerPrice;
    }

    @Override
    public void setOpponentOfferForSeller(double buyerPrice) {
        this.opponentOfferForSeller = buyerPrice;
    }

    @Override
    public List<AnomalyRecord> getBuyerViolations() {
        return buyerViolations;
    }

    @Override
    public List<AnomalyRecord> getSellerViolations() {
        return sellerViolations;
    }

    @Override
    public void close() {
        try {
            restTemplate.delete(buyerBaseUrl + "/agent/buyer/session/" + buyerSessionId);
        } catch (Exception ignored) {
        }
        try {
            restTemplate.delete(sellerBaseUrl + "/agent/seller/session/" + sellerSessionId);
        } catch (Exception ignored) {
        }
    }
}
