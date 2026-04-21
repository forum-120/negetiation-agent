package com.nego.simulator.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "nego.role", havingValue = "orchestrator", matchIfMissing = true)
public class MilvusConfig {

    public static final String MILVUS_HOST = "localhost";
    public static final int MILVUS_PORT = 19530;
    public static final String STRATEGY_COLLECTION_NAME = "negotiation_strategies";
    public static final int STRATEGY_EMBEDDING_DIMENSION = 768;

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
     * 集合不存在时由 LangChain4j 自动创建；是否写入知识由 StrategyKnowledgeService 决定
     */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return newEmbeddingStore(null);
    }

    public static ConnectParam newConnectParam() {
        return ConnectParam.newBuilder()
                .withHost(MILVUS_HOST)
                .withPort(MILVUS_PORT)
                .build();
    }

    public static MilvusEmbeddingStore newEmbeddingStore(MilvusServiceClient milvusClient) {
        MilvusEmbeddingStore.Builder builder = MilvusEmbeddingStore.builder()
                .collectionName(STRATEGY_COLLECTION_NAME)
                .dimension(STRATEGY_EMBEDDING_DIMENSION)
                .consistencyLevel(ConsistencyLevelEnum.STRONG)
                .autoFlushOnInsert(false);

        if (milvusClient != null) {
            return builder.milvusClient(milvusClient).build();
        }

        return builder
                .host(MILVUS_HOST)
                .port(MILVUS_PORT)
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
