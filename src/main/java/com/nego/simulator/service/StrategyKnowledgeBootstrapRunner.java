package com.nego.simulator.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression(
        "'${nego.role:orchestrator}' == 'orchestrator' and '${nego.rag.bootstrap:false}' == 'true'")
public class StrategyKnowledgeBootstrapRunner implements ApplicationRunner {

    private final MilvusStrategyStoreManager milvusStrategyStoreManager;
    private final ConfigurableApplicationContext applicationContext;

    public StrategyKnowledgeBootstrapRunner(MilvusStrategyStoreManager milvusStrategyStoreManager,
                                            ConfigurableApplicationContext applicationContext) {
        this.milvusStrategyStoreManager = milvusStrategyStoreManager;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        milvusStrategyStoreManager.rebuildKnowledgeBase();
        System.out.println("[RAG Bootstrap] 任务完成，进程退出。");
        int exitCode = SpringApplication.exit(applicationContext, () -> 0);
        System.exit(exitCode);
    }
}
