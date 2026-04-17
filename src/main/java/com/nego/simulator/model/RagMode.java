package com.nego.simulator.model;

/**
 * 实验的RAG策略模式
 */
public enum RagMode {
    NONE, // 都不用RAG
    BOTH, // 双方都用
    BUYER_ONLY, // 仅买方用
    SELLER_ONLY // 仅卖方用
}
