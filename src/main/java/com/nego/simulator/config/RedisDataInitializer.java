package com.nego.simulator.config;

import com.nego.simulator.model.ServiceInfo;
import com.nego.simulator.service.RedisProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "nego.role", havingValue = "orchestrator", matchIfMissing = true)
public class RedisDataInitializer implements CommandLineRunner {

    private final RedisProductRepository productRepository;

    public RedisDataInitializer(RedisProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public void run(String... args) {
        // 幂等检查：Redis 中已有数据则跳过
        if (productRepository.hasAnyProduct()) {
            System.out.println("[初始化] Redis 中已有商品数据，跳过预加载。");
            return;
        }

        // 预置 8 个商品，覆盖数据服务、算力服务、API 服务、AI 平台四大品类
        List<ServiceInfo> products = List.of(
                ServiceInfo.builder()
                        .id("svc-001")
                        .name("AI 数据标注服务")
                        .description("提供高质量的 NLP 数据标注，支持命名实体识别和情感分析")
                        .askingPrice(150.0)
                        .category("数据服务")
                        .build(),

                ServiceInfo.builder()
                        .id("svc-002")
                        .name("云端 GPU 算力租赁")
                        .description("A100 GPU 按小时租赁，适合模型训练和推理任务")
                        .askingPrice(200.0)
                        .category("算力服务")
                        .build(),

                ServiceInfo.builder()
                        .id("svc-003")
                        .name("智能客服 API")
                        .description("基于大模型的智能客服接口，支持多轮对话和意图识别")
                        .askingPrice(100.0)
                        .category("API 服务")
                        .build(),

                ServiceInfo.builder()
                        .id("svc-004")
                        .name("自然语言处理平台")
                        .description("提供文本分类、关键词提取、摘要生成等 NLP 能力的一站式平台")
                        .askingPrice(280.0)
                        .category("AI 平台")
                        .build(),

                ServiceInfo.builder()
                        .id("svc-005")
                        .name("图像识别 API")
                        .description("支持物体检测、人脸识别、OCR 文字识别的视觉 AI 接口")
                        .askingPrice(120.0)
                        .category("API 服务")
                        .build(),

                ServiceInfo.builder()
                        .id("svc-006")
                        .name("数据清洗与预处理")
                        .description("针对结构化和非结构化数据的自动化清洗、去重、格式标准化服务")
                        .askingPrice(90.0)
                        .category("数据服务")
                        .build(),

                ServiceInfo.builder()
                        .id("svc-007")
                        .name("大模型微调服务")
                        .description("基于企业私有数据对基础模型进行 LoRA 微调，交付专属领域模型")
                        .askingPrice(350.0)
                        .category("AI 平台")
                        .build(),

                ServiceInfo.builder()
                        .id("svc-008")
                        .name("实时语音转写 API")
                        .description("低延迟实时语音识别接口，支持中英文混合及方言识别")
                        .askingPrice(160.0)
                        .category("API 服务")
                        .build()
        );

        // 批量保存，每个商品写入 Redis String + Set 索引
        products.forEach(productRepository::save);
        System.out.println("[初始化] 商品数据已预加载到 Redis，共 " + products.size() + " 条。");
    }
}
