package com.nego.simulator.service;

import com.nego.simulator.model.ServiceInfo;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class InMemoryProductRepository {

    private static final List<ServiceInfo> PRODUCTS = List.of(
            ServiceInfo.builder()
                    .id("svc-cheap")
                    .name("数据标注服务")
                    .description("基础数据标注，适合小规模 NLP 任务")
                    .askingPrice(80.0)
                    .category("数据服务")
                    .build(),
            ServiceInfo.builder()
                    .id("svc-mid")
                    .name("智能客服 API")
                    .description("基于大模型的智能客服接口，支持多轮对话和意图识别")
                    .askingPrice(200.0)
                    .category("API 服务")
                    .build(),
            ServiceInfo.builder()
                    .id("svc-expensive")
                    .name("大模型微调平台")
                    .description("企业级模型微调与部署一站式平台")
                    .askingPrice(500.0)
                    .category("AI 平台")
                    .build()
    );

    public Optional<ServiceInfo> findById(String id) {
        return PRODUCTS.stream().filter(p -> p.getId().equals(id)).findFirst();
    }

    public List<ServiceInfo> findAll() {
        return PRODUCTS;
    }
}