package com.nego.simulator.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nego.simulator.model.BuyerStrategy;
import com.nego.simulator.model.SellerStrategy;
import com.nego.simulator.service.A2aHttpTransport;
import com.nego.simulator.service.AgentTransport;
import com.nego.simulator.service.HttpTransport;
import com.nego.simulator.service.InProcessTransport;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class TransportConfig {

    public interface TransportFactory {
        AgentTransport create(BuyerStrategy buyerStrategy, SellerStrategy sellerStrategy, double askingPrice);
    }

    @Configuration
    @ConditionalOnProperty(name = "nego.transport", havingValue = "inprocess", matchIfMissing = true)
    static class InProcessConfig {

        @Bean
        public TransportFactory transportFactory(ChatLanguageModel chatLanguageModel) {
            return (bs, ss, ap) -> new InProcessTransport(chatLanguageModel, bs, ss, ap);
        }
    }

    @Configuration
    @ConditionalOnProperty(name = "nego.transport", havingValue = "http")
    static class HttpConfig {

        @Bean
        public RestTemplate restTemplate() {
            return new RestTemplate();
        }

        @Bean
        public TransportFactory transportFactory(RestTemplate restTemplate,
                                                   @Value("${nego.agent.buyer.url}") String buyerUrl,
                                                   @Value("${nego.agent.seller.url}") String sellerUrl) {
            return (bs, ss, ap) -> new HttpTransport(restTemplate, buyerUrl, sellerUrl, bs, ss, ap);
        }
    }

    @Configuration
    @ConditionalOnProperty(name = "nego.transport", havingValue = "a2a")
    static class A2aConfig {

        @Bean
        public TransportFactory transportFactory(ObjectMapper objectMapper,
                                                   @Value("${nego.agent.buyer.url}") String buyerUrl,
                                                   @Value("${nego.agent.seller.url}") String sellerUrl) {
            return (bs, ss, ap) -> new A2aHttpTransport(buyerUrl, sellerUrl, bs, ss, ap, objectMapper);
        }
    }
}
