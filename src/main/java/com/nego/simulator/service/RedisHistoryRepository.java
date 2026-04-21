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

    private static final String HISTORY_KEY = "negotiation:history";
    private static final String HISTORY_KEY_PREFIX = "negotiation:history:";
    private static final String ANOMALIES_KEY = "negotiation:anomalies:all";
    private static final String ANOMALIES_KEY_PREFIX = "negotiation:anomalies:";
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

    public void saveResult(NegotiationResult result) {
        try {
            String json = objectMapper.writeValueAsString(result);
            redisTemplate.opsForList().rightPush(HISTORY_KEY, json);
            if (result.getProtocol() != null) {
                redisTemplate.opsForList().rightPush(HISTORY_KEY_PREFIX + result.getProtocol(), json);
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("谈判历史序列化失败", e);
        }
    }

    public List<NegotiationResult> findAllResults() {
        List<Object> jsons = redisTemplate.opsForList().range(HISTORY_KEY, 0, -1);
        if (jsons == null) return new ArrayList<>();

        List<NegotiationResult> results = new ArrayList<>();
        for (Object json : jsons) {
            try {
                results.add(objectMapper.readValue(json.toString(), NegotiationResult.class));
            } catch (JsonProcessingException e) {
                System.err.println("[历史] 跳过损坏记录: " + e.getMessage());
            }
        }
        return results;
    }

    public List<NegotiationResult> findResultsByProtocol(String protocol) {
        List<Object> jsons = redisTemplate.opsForList().range(HISTORY_KEY_PREFIX + protocol, 0, -1);
        if (jsons == null) return new ArrayList<>();

        List<NegotiationResult> results = new ArrayList<>();
        for (Object json : jsons) {
            try {
                results.add(objectMapper.readValue(json.toString(), NegotiationResult.class));
            } catch (JsonProcessingException e) {
                System.err.println("[历史] 跳过损坏记录: " + e.getMessage());
            }
        }
        return results;
    }

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

    public void saveAnomalies(List<AnomalyRecord> anomalies) {
        saveAnomalies(anomalies, null);
    }

    public void saveAnomalies(List<AnomalyRecord> anomalies, String protocol) {
        if (anomalies == null || anomalies.isEmpty()) return;

        for (AnomalyRecord anomaly : anomalies) {
            try {
                String json = objectMapper.writeValueAsString(anomaly);
                redisTemplate.opsForList().rightPush(ANOMALIES_KEY, json);
                if (protocol != null) {
                    redisTemplate.opsForList().rightPush(ANOMALIES_KEY_PREFIX + protocol, json);
                }
            } catch (JsonProcessingException e) {
                System.err.println("单独持久化 Anomaly 报错");
            }
        }
    }

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
