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
@ConditionalOnProperty(name = "nego.role", havingValue = "buyer")
public class BuyerA2aServerConfig {

    @Bean
    public InMemoryTaskStore buyerTaskStore() {
        return new InMemoryTaskStore();
    }

    @Bean
    public PushNotificationConfigStore buyerPushConfigStore() {
        return new InMemoryPushNotificationConfigStore();
    }

    @Bean
    public PushNotificationSender buyerPushSender(PushNotificationConfigStore buyerPushConfigStore) {
        return new BasePushNotificationSender(buyerPushConfigStore);
    }

    @Bean
    public InMemoryQueueManager buyerQueueManager(InMemoryTaskStore buyerTaskStore) {
        return new InMemoryQueueManager((TaskStateProvider) buyerTaskStore);
    }

    @Bean
    public RequestHandler buyerRequestHandler(AgentExecutor buyerAgentExecutor,
                                               InMemoryTaskStore buyerTaskStore,
                                               InMemoryQueueManager buyerQueueManager,
                                               PushNotificationConfigStore buyerPushConfigStore,
                                               PushNotificationSender buyerPushSender) {
        Executor executor = Executors.newCachedThreadPool();
        return DefaultRequestHandler.create(
                buyerAgentExecutor, (TaskStore) buyerTaskStore, buyerQueueManager,
                buyerPushConfigStore, buyerPushSender, executor);
    }
}
