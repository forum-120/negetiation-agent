package com.nego.simulator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nego.simulator.model.AnomalyRecord;
import com.nego.simulator.model.BuyerStrategy;
import com.nego.simulator.model.OfferResponse;
import com.nego.simulator.model.SellerStrategy;
import io.a2a.client.Client;
import io.a2a.client.ClientEvent;
import io.a2a.client.TaskEvent;
import io.a2a.client.TaskUpdateEvent;
import io.a2a.client.http.A2ACardResolver;
import io.a2a.client.transport.jsonrpc.JSONRPCTransport;
import io.a2a.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;
import io.a2a.spec.AgentCard;
import io.a2a.spec.Artifact;
import io.a2a.spec.Message;
import io.a2a.spec.Part;
import io.a2a.spec.Task;
import io.a2a.spec.TaskState;
import io.a2a.spec.TextPart;
import io.a2a.spec.UpdateEvent;
import io.a2a.spec.TaskArtifactUpdateEvent;
import io.a2a.spec.TaskStatusUpdateEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class A2aHttpTransport implements AgentTransport {

    private final Client buyerClient;
    private final Client sellerClient;
    private final String buyerSessionId;
    private final String sellerSessionId;
    private final ObjectMapper objectMapper;

    private double opponentOfferForBuyer;
    private double opponentOfferForSeller;

    private final BuyerStrategy buyerStrategy;
    private final SellerStrategy sellerStrategy;
    private final double askingPrice;
    private boolean buyerInitialized = false;
    private boolean sellerInitialized = false;

    private final List<AnomalyRecord> buyerViolations = new ArrayList<>();
    private final List<AnomalyRecord> sellerViolations = new ArrayList<>();

    public A2aHttpTransport(String buyerBaseUrl, String sellerBaseUrl,
                            BuyerStrategy buyerStrategy, SellerStrategy sellerStrategy,
                            double askingPrice, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        try {
            A2ACardResolver buyerResolver = new A2ACardResolver(buyerBaseUrl);
            AgentCard buyerCard = buyerResolver.getAgentCard();
            this.buyerClient = Client.builder(buyerCard)
                    .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfigBuilder())
                    .build();

            A2ACardResolver sellerResolver = new A2ACardResolver(sellerBaseUrl);
            AgentCard sellerCard = sellerResolver.getAgentCard();
            this.sellerClient = Client.builder(sellerCard)
                    .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfigBuilder())
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize A2A clients: " + e.getMessage(), e);
        }

        this.buyerSessionId = UUID.randomUUID().toString();
        this.sellerSessionId = UUID.randomUUID().toString();
        this.buyerStrategy = buyerStrategy;
        this.sellerStrategy = sellerStrategy;
        this.askingPrice = askingPrice;
    }

    @Override
    public CompletableFuture<OfferResponse> sendToBuyer(String context) {
        Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("sessionId", buyerSessionId);
        metadata.put("opponentOffer", opponentOfferForBuyer);
        if (!buyerInitialized) {
            metadata.put("strategy", buyerStrategy.name());
            metadata.put("askingPrice", askingPrice);
            buyerInitialized = true;
        }

        Message msg = new Message(
                Message.Role.USER,
                List.of(new TextPart(context)),
                UUID.randomUUID().toString(), null, null,
                List.of(),
                metadata,
                List.of()
        );

        CompletableFuture<OfferResponse> future = new CompletableFuture<>();

        try {
            buyerClient.sendMessage(msg,
                    List.of((event, card) -> {
                        if (event instanceof TaskEvent te) {
                            Task task = te.getTask();
                            if (task.getStatus().state() == TaskState.COMPLETED) {
                                try {
                                    OfferResponse offer = extractBuyerOfferFromTask(task);
                                    future.complete(offer);
                                } catch (Exception e) {
                                    future.completeExceptionally(e);
                                }
                            } else if (task.getStatus().state() == TaskState.FAILED) {
                                future.completeExceptionally(
                                        new RuntimeException("Buyer agent task failed"));
                            }
                        }
                    }),
                    err -> future.completeExceptionally(err),
                    null
            );
        } catch (Exception e) {
            future.completeExceptionally(e);
        }

        return future;
    }

    @Override
    public CompletableFuture<OfferResponse> sendToSeller(String context) {
        Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("sessionId", sellerSessionId);
        metadata.put("opponentOffer", opponentOfferForSeller);
        if (!sellerInitialized) {
            metadata.put("strategy", sellerStrategy.name());
            metadata.put("askingPrice", askingPrice);
            sellerInitialized = true;
        }

        Message msg = new Message(
                Message.Role.USER,
                List.of(new TextPart(context)),
                UUID.randomUUID().toString(), null, null,
                List.of(),
                metadata,
                List.of()
        );

        CompletableFuture<OfferResponse> future = new CompletableFuture<>();

        try {
            sellerClient.sendMessage(msg,
                    List.of((event, card) -> {
                        if (event instanceof TaskEvent te) {
                            Task task = te.getTask();
                            if (task.getStatus().state() == TaskState.COMPLETED) {
                                try {
                                    OfferResponse offer = extractSellerOfferFromTask(task);
                                    future.complete(offer);
                                } catch (Exception e) {
                                    future.completeExceptionally(e);
                                }
                            } else if (task.getStatus().state() == TaskState.FAILED) {
                                future.completeExceptionally(
                                        new RuntimeException("Seller agent task failed"));
                            }
                        }
                    }),
                    err -> future.completeExceptionally(err),
                    null
            );
        } catch (Exception e) {
            future.completeExceptionally(e);
        }

        return future;
    }

    private OfferResponse extractBuyerOfferFromTask(Task task) throws Exception {
        OfferResponse offer = extractOfferFromTask(task);
        if (offer.getViolations() != null) {
            buyerViolations.addAll(offer.getViolations());
        }
        return offer;
    }

    private OfferResponse extractSellerOfferFromTask(Task task) throws Exception {
        OfferResponse offer = extractOfferFromTask(task);
        if (offer.getViolations() != null) {
            sellerViolations.addAll(offer.getViolations());
        }
        return offer;
    }

    private OfferResponse extractOfferFromTask(Task task) throws Exception {
        if (task.getArtifacts() == null || task.getArtifacts().isEmpty()) {
            throw new RuntimeException("No artifacts in completed task");
        }
        Artifact artifact = task.getArtifacts().get(0);
        String json = artifact.parts().stream()
                .filter(p -> p instanceof TextPart)
                .map(p -> ((TextPart) p).getText())
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No text part in artifact"));

        OfferResponse offer = objectMapper.readValue(json, OfferResponse.class);
        return offer;
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
        try { buyerClient.close(); } catch (Exception ignored) {}
        try { sellerClient.close(); } catch (Exception ignored) {}
    }
}
