package com.nego.simulator.service;

import com.nego.simulator.config.MilvusConfig;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.GetLoadStateResponse;
import io.milvus.grpc.LoadState;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.DropCollectionParam;
import io.milvus.param.collection.GetCollectionStatisticsParam;
import io.milvus.param.collection.GetLoadStateParam;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.response.GetCollStatResponseWrapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "nego.role", havingValue = "orchestrator", matchIfMissing = true)
public class MilvusStrategyStoreManager {

    private static final long MILVUS_POLL_INTERVAL_MS = 3000L;

    private final EmbeddingModel embeddingModel;

    public MilvusStrategyStoreManager(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public void verifyKnowledgeBaseReady() {
        List<String> segments = readKnowledgeSegments();
        long expectedRowCount = segments.size();

        MilvusServiceClient client = new MilvusServiceClient(MilvusConfig.newConnectParam());
        try {
            if (!hasCollection(client, MilvusConfig.STRATEGY_COLLECTION_NAME)) {
                throw new IllegalStateException("Milvus 集合不存在，请先运行 RAG bootstrap。");
            }

            long actualRowCount = getCollectionRowCount(client, MilvusConfig.STRATEGY_COLLECTION_NAME);
            if (actualRowCount != expectedRowCount) {
                throw new IllegalStateException(
                        "Milvus 集合数据不正确，expected=" + expectedRowCount + ", actual=" + actualRowCount
                                + "。请先运行 RAG bootstrap 重建集合。");
            }

            requestCollectionLoad(client, MilvusConfig.STRATEGY_COLLECTION_NAME);
            waitForMilvusLoaded(MilvusConfig.STRATEGY_COLLECTION_NAME, client);
            System.out.println("[RAG] ✅ Milvus 校验通过，rowCount=" + actualRowCount + "。");
        } finally {
            client.close();
        }
    }

    public void rebuildKnowledgeBase() {
        List<String> segments = readKnowledgeSegments();

        MilvusServiceClient adminClient = new MilvusServiceClient(MilvusConfig.newConnectParam());
        try {
            if (hasCollection(adminClient, MilvusConfig.STRATEGY_COLLECTION_NAME)) {
                dropCollection(adminClient, MilvusConfig.STRATEGY_COLLECTION_NAME);
                System.out.println("[RAG Bootstrap] 已删除旧集合 " + MilvusConfig.STRATEGY_COLLECTION_NAME + "。");
            }
        } finally {
            adminClient.close();
        }

        MilvusServiceClient writerClient = new MilvusServiceClient(MilvusConfig.newConnectParam());
        try {
            MilvusEmbeddingStore writableStore = MilvusConfig.newEmbeddingStore(writerClient);
            for (String segmentText : segments) {
                TextSegment segment = TextSegment.from(segmentText);
                Embedding embedding = embeddingModel.embed(segment).content();
                writableStore.add(embedding, segment);
            }
            System.out.println("[RAG Bootstrap] 已写入 " + segments.size() + " 条策略。");

            long actualRowCount = getCollectionRowCount(writerClient, MilvusConfig.STRATEGY_COLLECTION_NAME);
            if (actualRowCount != segments.size()) {
                throw new IllegalStateException(
                        "RAG bootstrap 后数据量不正确，expected=" + segments.size() + ", actual=" + actualRowCount);
            }

            requestCollectionLoad(writerClient, MilvusConfig.STRATEGY_COLLECTION_NAME);
            waitForMilvusLoaded(MilvusConfig.STRATEGY_COLLECTION_NAME, writerClient);
            System.out.println("[RAG Bootstrap] ✅ Milvus 知识库重建完成。");
        } finally {
            writerClient.close();
        }
    }

    private List<String> readKnowledgeSegments() {
        try {
            ClassPathResource resource = new ClassPathResource("knowledge/negotiation_strategies.txt");
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return Arrays.stream(content.split("---"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new IllegalStateException("读取策略知识文件失败: " + e.getMessage(), e);
        }
    }

    private boolean hasCollection(MilvusServiceClient client, String collectionName) {
        R<Boolean> response = client.hasCollection(
                HasCollectionParam.newBuilder()
                        .withCollectionName(collectionName)
                        .build());
        ensureSuccess(response, "检查集合是否存在");
        return Boolean.TRUE.equals(response.getData());
    }

    private long getCollectionRowCount(MilvusServiceClient client, String collectionName) {
        R<io.milvus.grpc.GetCollectionStatisticsResponse> response = client.getCollectionStatistics(
                GetCollectionStatisticsParam.newBuilder()
                        .withCollectionName(collectionName)
                        .withFlush(true)
                        .build());
        ensureSuccess(response, "查询集合统计");
        return new GetCollStatResponseWrapper(response.getData()).getRowCount();
    }

    private void dropCollection(MilvusServiceClient client, String collectionName) {
        R<RpcStatus> response = client.dropCollection(
                DropCollectionParam.newBuilder()
                        .withCollectionName(collectionName)
                        .build());
        ensureSuccess(response, "删除集合");
    }

    private void requestCollectionLoad(MilvusServiceClient client, String collectionName) {
        R<RpcStatus> response = client.loadCollection(
                LoadCollectionParam.newBuilder()
                        .withCollectionName(collectionName)
                        .build());
        ensureSuccess(response, "请求加载集合");
    }

    private void waitForMilvusLoaded(String collectionName, MilvusServiceClient client) {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                R<GetLoadStateResponse> response = client.getLoadState(
                        GetLoadStateParam.newBuilder()
                                .withCollectionName(collectionName)
                                .build());
                ensureSuccess(response, "查询集合加载状态");

                GetLoadStateResponse loadState = response.getData();
                if (loadState.getState() == LoadState.LoadStateLoaded) {
                    return;
                }

                System.out.println("[RAG] ⏳ Milvus 集合状态: " + loadState.getState()
                        + "（第" + attempt + "次检查），继续等待...");
            } catch (Exception e) {
                System.out.println("[RAG] ⏳ 轮询 Milvus 状态出错，重试: " + e.getMessage());
            }

            try {
                Thread.sleep(MILVUS_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待 Milvus 加载时线程被中断", e);
            }
        }
    }

    private <T> void ensureSuccess(R<T> response, String action) {
        if (response == null) {
            throw new IllegalStateException(action + "失败：Milvus 返回空响应");
        }

        if (!Objects.equals(response.getStatus(), R.Status.Success.getCode())) {
            String message = response.getMessage() != null ? response.getMessage() : "unknown";
            throw new IllegalStateException(
                    action + "失败：status=" + response.getStatus() + ", message=" + message,
                    response.getException());
        }
    }
}
