package com.nego.simulator.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "nego.role", havingValue = "orchestrator", matchIfMissing = true)
public class MilvusConfig {

    /**
     * 配置本地embedding模型。
     * 使用Ollama的dmeta-embedding-zh，维度768
     * 在本地localhost:11434运行
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        return OllamaEmbeddingModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("shaw/dmeta-embedding-zh")
                .build();
    }

    /**
     * 配置Milvus向量存储
     * collectionName:Milvus里的集合名，类似数据库的表名
     * dimension:维度，必须和embedding模型输出的维度一致
     * dropCollectionOnStart：true表示每次启动先清空重建集合，保证向量数据和文本文件同步
     */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return MilvusEmbeddingStore.builder()
                .host("localhost")
                .port(19530)
                .collectionName("negotiation_strategies")
                .dimension(768)
                .build();
    }

    /**
     * 配置本地轻量级模型用于 RAG Query Rewrite。
     * 针对 deepseek-r1:1.5b 特别配置
     */
    @Bean("queryRewriteModel")
    public ChatLanguageModel queryRewriteModel() {
        return OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("deepseek-r1:1.5b")
                .temperature(0.1) // 极低温度，防止其过度发散
                .build();
    }

}
