package com.nego.simulator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nego.simulator.model.AnomalyRecord;
import com.nego.simulator.model.NegotiationMessage;
import com.nego.simulator.model.NegotiationResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
@ConditionalOnProperty(name = "nego.role", havingValue = "orchestrator", matchIfMissing = true)
public class RedisHistoryRepository {

    // 全局谈判历史 List 的 Key
    private static final String HISTORY_KEY = "negotiation:history";
    
    // 全局异常记录 List 的 Key (供 Dashboard 大盘分析)
    private static final String ANOMALIES_KEY = "negotiation:anomalies:all";

    // 单场谈判消息的 Key 前缀和后缀，完整格式：negotiation:{id}:messages
    private static final String MESSAGES_KEY_PREFIX = "negotiation:";
    private static final String MESSAGES_KEY_SUFFIX = ":messages";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisHistoryRepository(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * 追加一条谈判结果到全局历史列表。
     *
     * <p>执行：RPUSH negotiation:history jsonString</p>
     * <p>RPUSH 每次追加到 List 右侧（尾部），保证时间顺序。</p>
     */
    public void saveResult(NegotiationResult result) {
        try {
            String json = objectMapper.writeValueAsString(result);
            redisTemplate.opsForList().rightPush(HISTORY_KEY, json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("谈判历史序列化失败", e);
        }
    }

    /**
     * 读取全部历史记录。
     *
     * <p>执行：LRANGE negotiation:history 0 -1（0 到 -1 表示完整列表）</p>
     * <p>反序列化时跳过损坏的条目，保证接口不因为脏数据崩溃。</p>
     */
    public List<NegotiationResult> findAllResults() {
        List<Object> jsons = redisTemplate.opsForList().range(HISTORY_KEY, 0, -1);
        if (jsons == null) return new ArrayList<>();

        List<NegotiationResult> results = new ArrayList<>();
        for (Object json : jsons) {
            try {
                results.add(objectMapper.readValue(json.toString(), NegotiationResult.class));
            } catch (JsonProcessingException e) {
                // 跳过损坏的单条记录，不影响整体读取
                System.err.println("[历史] 跳过损坏记录: " + e.getMessage());
            }
        }
        return results;
    }

    /**
     * 按 negotiationId 保存这场谈判的逐轮消息。
     *
     * <p>执行：RPUSH negotiation:{id}:messages msg1Json msg2Json ...</p>
     * <p>每条消息独立追加，保留轮次顺序。</p>
     */
    public void saveMessages(String negotiationId, List<NegotiationMessage> messages) {
        String key = MESSAGES_KEY_PREFIX + negotiationId + MESSAGES_KEY_SUFFIX;
        for (NegotiationMessage msg : messages) {
            try {
                String json = objectMapper.writeValueAsString(msg);
                redisTemplate.opsForList().rightPush(key, json);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("消息序列化失败", e);
            }
        }
    }

    /**
     * 读取某场谈判的全部逐轮消息（按轮次顺序）。
     *
     * <p>执行：LRANGE negotiation:{id}:messages 0 -1</p>
     */
    public List<NegotiationMessage> findMessagesByNegotiationId(String negotiationId) {
        String key = MESSAGES_KEY_PREFIX + negotiationId + MESSAGES_KEY_SUFFIX;
        List<Object> jsons = redisTemplate.opsForList().range(key, 0, -1);
        if (jsons == null) return new ArrayList<>();

        List<NegotiationMessage> messages = new ArrayList<>();
        for (Object json : jsons) {
            try {
                messages.add(objectMapper.readValue(json.toString(), NegotiationMessage.class));
            } catch (JsonProcessingException e) {
                System.err.println("[消息] 跳过损坏记录: " + e.getMessage());
            }
        }
        return messages;
    }

    /**
     * 专门将异常剥离出来持久化，用于安全审计和 Dashboard
     */
    public void saveAnomalies(List<AnomalyRecord> anomalies) {
        if (anomalies == null || anomalies.isEmpty()) return;
        
        for (AnomalyRecord anomaly : anomalies) {
            try {
                String json = objectMapper.writeValueAsString(anomaly);
                redisTemplate.opsForList().rightPush(ANOMALIES_KEY, json);
            } catch (JsonProcessingException e) {
                System.err.println("单独持久化 Anomaly 报错");
            }
        }
    }

    /**
     * 获取全局范围内的所有异常事件
     */
    public List<AnomalyRecord> findAllAnomalies() {
        List<Object> jsons = redisTemplate.opsForList().range(ANOMALIES_KEY, 0, -1);
        List<AnomalyRecord> res = new ArrayList<>();
        if (jsons == null) return res;
        
        for (Object json : jsons) {
            try {
                res.add(objectMapper.readValue(json.toString(), AnomalyRecord.class));
            } catch (Exception e) {}
        }
        return res;
    }
}
