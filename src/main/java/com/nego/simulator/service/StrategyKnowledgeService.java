package com.nego.simulator.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnExpression(
        "'${nego.role:orchestrator}' == 'orchestrator' and '${nego.rag.bootstrap:false}' == 'false'")
public class StrategyKnowledgeService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ChatLanguageModel queryRewriteModel;
    private final MilvusStrategyStoreManager milvusStrategyStoreManager;

    public StrategyKnowledgeService(EmbeddingModel embeddingModel,
                                    EmbeddingStore<TextSegment> embeddingStore,
                                    @Qualifier("queryRewriteModel") ChatLanguageModel queryRewriteModel,
                                    MilvusStrategyStoreManager milvusStrategyStoreManager) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.queryRewriteModel = queryRewriteModel;
        this.milvusStrategyStoreManager = milvusStrategyStoreManager;
    }

    /**
     * 正常业务模式下只校验 Milvus 是否已由 bootstrap 准备好，不再做写入。
     */
    @PostConstruct
    public void verifyKnowledgeStore() {
        milvusStrategyStoreManager.verifyKnowledgeBaseReady();
    }

    public static class RagResult {
        public String rewrittenQuery;
        public String advice;
        public RagResult(String rewrittenQuery, String advice) {
            this.rewrittenQuery = rewrittenQuery;
            this.advice = advice;
        }
    }

    /**
     * 语义检索：根据当前谈判情景描述，找出最相关的 top1 策略。
     */
    public RagResult searchStrategy(String situationDescription) {

        // 0. Query Rewrite：用 Few-Shot 提示让小模型将数字化局势改写成语义化情景
        String prompt = String.format("""
                你是一个谈判分析系统。请根据下面给出的【当前详尽局势】，用高度概括、书面化的语言，提炼出当前的【面临情景】。
                要求：忽略具体数字金额，仅保留阶段、角色行为和差价定性。只需要输出最后的一句话提炼情景，不要包含任何前缀。

                【例子1】：
                当前局势：作为买方，第4轮，对方要150我给出140，已经三轮没怎么变了。
                提炼情景：进入谈判中后期，卖方展现出极强的抗拒妥协心理，双方价格差距极小但陷入停滞僵局。

                【例子2】：
                当前局势：作为卖方，第1轮，买方直接给我报了个30的超低价（原价100）。
                提炼情景：谈判初期，买方试图通过具有冲击力的极端低价实行锚定效应。

                现在，请对以下当前局势进行提炼情景：
                当前详尽局势：%s
                提炼情景：""", situationDescription);

        String rewrittenQuery;
        try {
            String response = queryRewriteModel.generate(prompt);
            // 剥离 deepseek-r1 推理模型的 <think>...</think> 思维链
            if (response.contains("</think>")) {
                rewrittenQuery = response.substring(response.lastIndexOf("</think>") + 8).trim();
            } else {
                rewrittenQuery = response.trim();
            }
            if (rewrittenQuery.startsWith("提炼情景：") || rewrittenQuery.startsWith("提炼情景:")) {
                rewrittenQuery = rewrittenQuery.substring(5).trim();
            }
            System.out.println("[RAG Query Rewrite] 原局势: " + situationDescription);
            System.out.println("[RAG Query Rewrite] DeepSeek改写后: " + rewrittenQuery);
        } catch (Exception e) {
            System.err.println("[RAG Query Rewrite] 失败降级: " + e.getMessage());
            rewrittenQuery = situationDescription;
        }

        // 1. Embedding
        Embedding queryEmbedding = embeddingModel.embed(rewrittenQuery).content();

        // 2. 阻塞重试直到 Milvus 返回结果（实验模式，绝不降级）
        List<EmbeddingMatch<TextSegment>> matches = null;
        int attempt = 0;
        while (matches == null) {
            attempt++;
            try {
                matches = embeddingStore.findRelevant(queryEmbedding, 1);
            } catch (Exception e) {
                String reason = e.getMessage() != null ? e.getMessage().split("\n")[0] : "unknown";
                System.err.println("[RAG] ⏳ Milvus 检索第" + attempt + "次失败，5秒后重试（实验模式不降级）。原因: " + reason);
                try { Thread.sleep(5000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }

        // 3. 拼接结果
        if (matches.isEmpty()) {
            return new RagResult(rewrittenQuery, "暂无相关谈判策略建议");
        }
        StringBuilder sb = new StringBuilder("[相关谈判策略建议] \n\n");
        for (int i = 0; i < matches.size(); i++) {
            sb.append("策略").append(i + 1).append(":\n");
            sb.append(matches.get(i).embedded().text());
            sb.append("\n\n");
        }
        return new RagResult(rewrittenQuery, sb.toString().trim());
    }
}
