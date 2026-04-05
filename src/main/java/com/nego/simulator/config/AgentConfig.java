package com.nego.simulator.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.dashscope.QwenChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 配置类。
 *
 * <p>负责注册 LLM（通义千问）的 ChatLanguageModel Bean。
 * 该 Bean 会被 NegotiationService 注入，用于在每次谈判时
 * 动态创建 BuyerAgent 和 SellerAgent 实例。</p>
 *
 * <p>为什么 Agent 不在这里注册为 Bean？
 * 因为 BuyerTools / SellerTools 是有状态的（记录 lastOffer、accepted），
 * 每场谈判都需要全新的 Tools 实例，所以 Agent 必须在 Service 中按需创建。</p>
 */
@Configuration
public class AgentConfig {

    @Value("${dashscope.api-key}")
    private String apiKey;

    @Value("${dashscope.model-name:qwen-max}")
    private String modelName;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return QwenChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .build();
    }
}
