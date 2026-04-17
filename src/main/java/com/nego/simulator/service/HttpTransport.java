package com.nego.simulator.service;

import com.nego.simulator.model.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * HttpTransport — AgentTransport 的 HTTP 远程调用实现。
 *
 * <p>通过 REST 调用远程 Buyer / Seller Agent 服务。
 * 当前实现在调用线程同步等待 HTTP 响应，然后包装成 completedFuture 返回。
 * Phase 2 如需真正并发，可改用 AsyncRestTemplate 或 WebClient。</p>
 */
public class HttpTransport implements AgentTransport {

    private final RestTemplate restTemplate;
    private final String buyerBaseUrl;
    private final String sellerBaseUrl;
    private final String buyerSessionId;
    private final String sellerSessionId;

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
    public CompletableFuture<OfferResponse> sendToBuyer(String context) {
        AgentCallRequest request = AgentCallRequest.builder()
                .sessionId(buyerSessionId)
                .context(context)
                .opponentLastOffer(opponentOfferForBuyer)
                .build();
        ResponseEntity<AgentCallResponse> resp = restTemplate.postForEntity(
                buyerBaseUrl + "/agent/buyer/call", request, AgentCallResponse.class);
        AgentCallResponse body = resp.getBody();

        if (body.getViolations() != null) {
            buyerViolations.addAll(body.getViolations());
        }

        return CompletableFuture.completedFuture(OfferResponse.builder()
                .text(body.getResponse())
                .lastOffer(body.getLastOffer())
                .accepted(body.isAccepted())
                .violations(body.getViolations())
                .build());
    }

    @Override
    public CompletableFuture<OfferResponse> sendToSeller(String context) {
        AgentCallRequest request = AgentCallRequest.builder()
                .sessionId(sellerSessionId)
                .context(context)
                .opponentLastOffer(opponentOfferForSeller)
                .build();
        ResponseEntity<AgentCallResponse> resp = restTemplate.postForEntity(
                sellerBaseUrl + "/agent/seller/call", request, AgentCallResponse.class);
        AgentCallResponse body = resp.getBody();

        if (body.getViolations() != null) {
            sellerViolations.addAll(body.getViolations());
        }

        return CompletableFuture.completedFuture(OfferResponse.builder()
                .text(body.getResponse())
                .lastOffer(body.getLastOffer())
                .accepted(body.isAccepted())
                .violations(body.getViolations())
                .build());
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
