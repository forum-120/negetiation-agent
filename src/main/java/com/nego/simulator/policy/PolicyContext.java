package com.nego.simulator.policy;

import com.nego.simulator.model.NegotiateProtocol;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PolicyContext {

    private double originalPrice;
    private double buyerCeiling;
    private double sellerFloor;

    private double buyerOffer;
    private double sellerOffer;
    private double settlementPrice;

    private int currentRound;
    private int maxRounds;
    private String negotiationId;
    private NegotiateProtocol protocol;
}