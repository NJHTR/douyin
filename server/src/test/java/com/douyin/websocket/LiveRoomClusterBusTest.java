package com.douyin.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveRoomClusterBusTest {

    @Test
    void publishesRoomEnvelopeAndDeliversRemoteEventWithoutLooping() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<LiveStreamHandler> provider = mock(ObjectProvider.class);
        RedisMessageListenerContainer container = mock(RedisMessageListenerContainer.class);
        LiveStreamHandler handler = mock(LiveStreamHandler.class);
        when(provider.getIfAvailable()).thenReturn(handler);
        LiveRoomClusterBus bus = new LiveRoomClusterBus(
                "node-a", "live-fanout", redis, new ObjectMapper(), provider, container);

        bus.publish(42L, "{\"type\":\"chat\"}", false);
        bus.onMessage(message("{\"origin_node\":\"node-b\",\"room_id\":42,"
                + "\"payload\":\"hello\",\"close_after\":true}"), null);
        bus.onMessage(message("{\"origin_node\":\"node-a\",\"room_id\":42,"
                + "\"payload\":\"loop\",\"close_after\":false}"), null);

        verify(container).addMessageListener(eq(bus), any(Topic.class));
        verify(redis).convertAndSend(eq("live-fanout"), contains("\"room_id\":42"));
        verify(handler).deliverClusterEvent(42L, "hello", true);
        verify(handler, never()).deliverClusterEvent(42L, "loop", false);
    }

    private static DefaultMessage message(String body) {
        return new DefaultMessage("live-fanout".getBytes(StandardCharsets.UTF_8),
                body.getBytes(StandardCharsets.UTF_8));
    }
}
