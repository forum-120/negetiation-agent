package com.nego.simulator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nego.simulator.model.ServiceInfo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
@ConditionalOnProperty(name = "nego.role", havingValue = "orchestrator", matchIfMissing = true)
public class RedisProductRepository {

    // 每个商品的 Key 前缀，完整格式：product:svc-001
    private static final String PRODUCT_KEY_PREFIX = "product:";

    // 全局商品 id 索引，使用 Set 结构存所有商品 id
    // 用 Set 而不是 List 是因为 id 天然无序且不重复，Set 的 SADD/SMEMBERS 操作更语义化
    private static final String PRODUCT_INDEX_KEY = "product:index";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisProductRepository(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        // 单独创建 ObjectMapper 实例，避免影响全局 Bean
        this.objectMapper = new ObjectMapper();
        // 注册 Java 8 时间模块，支持 LocalDateTime 序列化
        this.objectMapper.registerModule(new JavaTimeModule());
        // 关闭"把时间写成时间戳数字"的默认行为，改为写成 ISO 字符串
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * 保存或更新一个商品。
     *
     * <p>执行两个 Redis 操作（非原子，生产可用 Lua 脚本）：</p>
     * <ol>
     *     <li>SET product:{id} jsonString  → 存商品 JSON</li>
     *     <li>SADD product:index {id}      → 把 id 加入全局索引</li>
     * </ol>
     */
    public void save(ServiceInfo product) {
        try {
            String json = objectMapper.writeValueAsString(product);
            // 存商品 JSON，Key 示例：product:svc-001
            redisTemplate.opsForValue().set(PRODUCT_KEY_PREFIX + product.getId(), json);
            // 把 id 加入全局索引 Set，用于 findAll 遍历
            redisTemplate.opsForSet().add(PRODUCT_INDEX_KEY, product.getId());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("商品序列化失败: " + product.getId(), e);
        }
    }

    /**
     * 根据 id 查找商品。返回 Optional，调用方判断是否存在。
     *
     * <p>执行：GET product:{id}，反序列化为 ServiceInfo 对象。</p>
     */
    public Optional<ServiceInfo> findById(String id) {
        Object json = redisTemplate.opsForValue().get(PRODUCT_KEY_PREFIX + id);
        if (json == null) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(json.toString(), ServiceInfo.class));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("商品反序列化失败: " + id, e);
        }
    }

    /**
     * 获取所有商品列表，按 id 字典序排序保证顺序稳定。
     *
     * <p>执行：SMEMBERS product:index → 遍历每个 id 调用 GET。</p>
     */
    public List<ServiceInfo> findAll() {
        Set<Object> ids = redisTemplate.opsForSet().members(PRODUCT_INDEX_KEY);
        if (ids == null || ids.isEmpty()) return new ArrayList<>();

        List<ServiceInfo> result = new ArrayList<>();
        for (Object id : ids) {
            findById(id.toString()).ifPresent(result::add);
        }
        // 按 id 排序，保证每次返回顺序一致（Redis Set 本身无序）
        result.sort((a, b) -> a.getId().compareTo(b.getId()));
        return result;
    }

    /**
     * 判断 Redis 中是否已有商品数据。
     * 用于 RedisDataInitializer 启动时判断是否需要预加载，避免重复写入。
     */
    public boolean hasAnyProduct() {
        Long size = redisTemplate.opsForSet().size(PRODUCT_INDEX_KEY);
        return size != null && size > 0;
    }
}
