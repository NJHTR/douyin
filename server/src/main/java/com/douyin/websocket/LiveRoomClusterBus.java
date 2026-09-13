package com.douyin.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Low-latency room control fanout. Durable room state remains authoritative in MySQL. */
@Slf4j
@Component
@ConditionalOnProperty(name = "websocket.cluster.enabled", havingValue = "true")
public class LiveRoomClusterBus implements MessageListener {

    private final String nodeId;
    private final String channel;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<LiveStreamHandler> handler;

    public LiveRoomClusterBus(@Value("${websocket.cluster.node-id:${HOSTNAME:local}}") String nodeId,
            @Value("${websocket.cluster.live-channel:douyin:websocket:live-fanout:v1}") String channel,
            StringRedisTemplate redis, ObjectMapper objectMapper,
            ObjectProvider<LiveStreamHandler> handler,
            RedisMessageListenerContainer listenerContainer) {
        this.nodeId = nodeId;
        this.channel = channel;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.handler = handler;
        listenerContainer.addMessageListener(this, new ChannelTopic(channel));
    }

    public void publish(Long roomId, String payload, boolean closeAfter) {
        if (roomId == null || payload == null) return;
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("origin_node", nodeId);
            envelope.put("room_id", roomId);
            envelope.put("payload", payload);
            envelope.put("close_after", closeAfter);
            redis.convertAndSend(channel, objectMapper.writeValueAsString(envelope));
        } catch (Exception e) {
            log.warn("[LIVE-CLUSTER] room event publish failed: {}", e.getMessage());
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = objectMapper.readValue(message.getBody(), Map.class);
            if (nodeId.equals(String.valueOf(envelope.get("origin_node")))) return;
            Object rawRoomId = envelope.get("room_id");
            Object payload = envelope.get("payload");
            if (rawRoomId == null || payload == null) return;
            long roomId = rawRoomId instanceof Number number
                    ? number.longValue() : Long.parseLong(String.valueOf(rawRoomId));
            boolean closeAfter = Boolean.parseBoolean(String.valueOf(envelope.get("close_after")));
            LiveStreamHandler target = handler.getIfAvailable();
            if (target != null) target.deliverClusterEvent(roomId, String.valueOf(payload), closeAfter);
        } catch (Exception e) {
            log.warn("[LIVE-CLUSTER] invalid room fanout message ignored: {}", e.getMessage());
        }
    }
}
