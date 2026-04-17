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

import java.lang.reflect.Proxy;
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
        DefaultRequestHandler impl = new DefaultRequestHandler(
                buyerAgentExecutor, (TaskStore) buyerTaskStore, buyerQueueManager,
                buyerPushConfigStore, buyerPushSender, executor);
        // 设置 timeout 字段（字段是 package-private，需要反射）
        setTimeoutFields(impl);
        // 用 JDK 动态代理包装：Spring 组可能看到的类型是 RequestHandler 接口，
        // 而不是 DefaultRequestHandler 本体，就不会扫描其内部的 @jakarta.inject.Inject 字段。
        return (RequestHandler) Proxy.newProxyInstance(
                RequestHandler.class.getClassLoader(),
                new Class[]{RequestHandler.class},
                (proxy, method, args) -> method.invoke(impl, args));
    }

    private void setTimeoutFields(DefaultRequestHandler handler) {
        try {
            java.lang.reflect.Field agentTimeout = DefaultRequestHandler.class
                    .getDeclaredField("agentCompletionTimeoutSeconds");
            agentTimeout.setAccessible(true);
            agentTimeout.set(handler, 90);

            java.lang.reflect.Field consumptionTimeout = DefaultRequestHandler.class
                    .getDeclaredField("consumptionCompletionTimeoutSeconds");
            consumptionTimeout.setAccessible(true);
            consumptionTimeout.set(handler, 10);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set DefaultRequestHandler timeout fields", e);
        }
    }
}
