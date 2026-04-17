package com.nego.simulator.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "nego.role", havingValue = "orchestrator", matchIfMissing = true)
public class StrategyKnowledgeService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ChatLanguageModel queryRewriteModel;

    public StrategyKnowledgeService(EmbeddingModel embeddingModel, 
                                    EmbeddingStore<TextSegment> embeddingStore,
                                    @Qualifier("queryRewriteModel") ChatLanguageModel queryRewriteModel) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.queryRewriteModel = queryRewriteModel;
    }

    /**
     * Spring启动完成后自动执行，完成知识库加载流程
     * 
     * 流程：读取文本 -> 按‘---‘切割成段 -> Embedding -> 存入Milvus
     * MilvusConfig 中 dropCollectionOnStart=true，所以每次启动都是全量重写，不会重复）
     */
    @PostConstruct
    public void loadKnowledge() {
        try {
            // 1.从classpath读取策略文本文件
            ClassPathResource resource = new ClassPathResource("knowledge/negotiation_strategies.txt");
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            // 2.按’---’分割成策略段落
            List<String> segments = Arrays.stream(content.split("---"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());

            // 3. 对每个策略段进行Embedding并存入Milvus
            for (String segmentText : segments) {
                TextSegment segment = TextSegment.from(segmentText);
                Embedding embedding = embeddingModel.embed(segment).content();
                embeddingStore.add(embedding, segment);
            }
            System.out.println("[RAG] 成功加载 " + segments.size() + " 条谈判策略到 Milvus");

        } catch (Exception e) {
            // 原来只 catch IOException，但 Milvus/Ollama 连接失败抛的是 RuntimeException。
            // 若不 catch 所有异常，@PostConstruct 崩溃会导致 Spring Context 启动失败，
            // 所有接口均返回 500。这里改为宽口 catch + 警告，应用照常启动，
            // RAG 功能降级（ragMode=NONE 时不影响谈判）。
            System.err.println("[RAG] ⚠️  知识库加载失败，RAG 功能将不可用。原因：" + e.getMessage());
            System.err.println("[RAG] 请确认 Ollama（localhost:11434）和 Milvus（localhost:19530）已启动。");
        }
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
     * 语义检索：根据当前谈判情景描述，找出最相关的top1策略
     * 
     * @param situationDescription 谈判情景描述
     * @return 包含改写 query 和 具体策略的对像
     */
    public RagResult searchStrategy(String situationDescription) {
        
        // 0. 按照 Few-Shot 对称性对齐原则构造 Query Rewrite
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
            // 依赖本地 deepseek-r1:1.5b 生成
            String response = queryRewriteModel.generate(prompt);
            
            // 重要：因为使用的是 deepseek-r1 推理模型，它会自动输出 <think>...</think> 标签里的思维链
            // 为防止无用的思维链污染 Embedding 空间，必须做物理剥离
            if (response.contains("</think>")) {
                rewrittenQuery = response.substring(response.lastIndexOf("</think>") + 8).trim();
            } else {
                rewrittenQuery = response.trim();
            }
            
            // 去除可能出现的前缀（如 "提炼情景："），进一步清洗以保持纯净度
            if (rewrittenQuery.startsWith("提炼情景：") || rewrittenQuery.startsWith("提炼情景:")) {
                rewrittenQuery = rewrittenQuery.substring(5).trim();
            }

            System.out.println("[RAG Query Rewrite] 原局势: " + situationDescription);
            System.out.println("[RAG Query Rewrite] DeepSeek改写后: " + rewrittenQuery);
            
        } catch (Exception e) {
            System.err.println("[RAG Query Rewrite] 失败降级: " + e.getMessage());
            // 降级兜底：万一小模型卡死，用原局势去检索，避免本轮谈判死循环抛错
            rewrittenQuery = situationDescription;
        }

        // 1.对改写后的纯文本进行Embedding
        Embedding queryEmbedding = embeddingModel.embed(rewrittenQuery).content();

        // 2.在milvus中作余弦相似度检索，取top1
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.findRelevant(queryEmbedding, 1);

        // 3.把检索结果拼接成可读文本返回给Agent
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
