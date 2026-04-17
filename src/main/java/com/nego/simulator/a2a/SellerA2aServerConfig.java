package com.nego.simulator.a2a;

import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.events.InMemoryQueueManager;
import io.a2a.server.requesthandlers.DefaultRequestHandler;
import io.a2a.server.requesthandlers.RequestHandler;
import io.a2a.server.tasks.BasePushNotificationSender;
import io.a2a.server.tasks.InMemoryPushNotificationConfigStore;
import io.a2a.server.tasks.InMemoryTaskStore;
import io.a2a.server.tasks.PushNotificationConfigStore;
import io.a2a.server.tasks.PushNotificationSender;
import io.a2a.server.tasks.TaskStateProvider;
import io.a2a.server.tasks.TaskStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
@ConditionalOnProperty(name = "nego.role", havingValue = "seller")
public class SellerA2aServerConfig {

    @Bean
    public InMemoryTaskStore sellerTaskStore() {
        return new InMemoryTaskStore();
    }

    @Bean
    public PushNotificationConfigStore sellerPushConfigStore() {
        return new InMemoryPushNotificationConfigStore();
    }

    @Bean
    public PushNotificationSender sellerPushSender(PushNotificationConfigStore sellerPushConfigStore) {
        return new BasePushNotificationSender(sellerPushConfigStore);
    }

    @Bean
    public InMemoryQueueManager sellerQueueManager(InMemoryTaskStore sellerTaskStore) {
        return new InMemoryQueueManager((TaskStateProvider) sellerTaskStore);
    }

    @Bean
    public RequestHandler sellerRequestHandler(AgentExecutor sellerAgentExecutor,
                                                InMemoryTaskStore sellerTaskStore,
                                                InMemoryQueueManager sellerQueueManager,
                                                PushNotificationConfigStore sellerPushConfigStore,
                                                PushNotificationSender sellerPushSender) {
        Executor executor = Executors.newCachedThreadPool();
        return DefaultRequestHandler.create(
                sellerAgentExecutor, (TaskStore) sellerTaskStore, sellerQueueManager,
                sellerPushConfigStore, sellerPushSender, executor);
    }
}
