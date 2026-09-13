package com.douyin.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisRecommendationPoolTest {

    @Test
    void recommendationPoolUsesPlainJsonSoReplicasShareTheSameWireFormat() {
        RedisTemplate<String, Object> generic = mock(RedisTemplate.class);
        StringRedisTemplate strings = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(strings.opsForValue()).thenReturn(values);

        RedisCacheService service = new RedisCacheService(generic, strings);
        service.putRecommendPool(991001L, FeedChannel.HOME, "browser-a", List.of(11L, 12L, 11L));

        verify(values).set(anyString(), org.mockito.ArgumentMatchers.eq("[11,12]"),
                any(Duration.class));
    }

    @Test
    void recommendationPoolReadsPlainJsonAndLegacyTypedArray() {
        RedisTemplate<String, Object> generic = mock(RedisTemplate.class);
        StringRedisTemplate strings = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(strings.opsForValue()).thenReturn(values);
        RedisCacheService service = new RedisCacheService(generic, strings);

        when(values.get(anyString())).thenReturn("[11,12,11]");
        assertEquals(List.of(11L, 12L),
                service.getRecommendPool(991001L, FeedChannel.HOME, "browser-a").orElseThrow());

        when(values.get(anyString())).thenReturn("[\"java.util.ArrayList\",[21,22,21]]");
        assertEquals(List.of(21L, 22L),
                service.getRecommendPool(991001L, FeedChannel.HOME, "browser-a").orElseThrow());
    }

    @Test
    void recommendationPoolIsSharedBySeparateServiceInstances() {
        RedisTemplate<String, Object> generic = mock(RedisTemplate.class);
        StringRedisTemplate stringsA = mock(StringRedisTemplate.class);
        StringRedisTemplate stringsB = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valuesA = mock(ValueOperations.class);
        ValueOperations<String, String> valuesB = mock(ValueOperations.class);
        when(stringsA.opsForValue()).thenReturn(valuesA);
        when(stringsB.opsForValue()).thenReturn(valuesB);

        Map<String, String> sharedRedis = new HashMap<>();
        when(valuesB.get(anyString())).thenAnswer(invocation ->
                sharedRedis.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            sharedRedis.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valuesA).set(anyString(), anyString(), any(Duration.class));

        RedisCacheService apiA = new RedisCacheService(generic, stringsA);
        RedisCacheService apiB = new RedisCacheService(generic, stringsB);

        apiA.putRecommendPool(991001L, FeedChannel.HOME, "browser-a", List.of(41L, 42L));

        assertEquals(List.of(41L, 42L),
                apiB.getRecommendPool(991001L, FeedChannel.HOME, "browser-a").orElseThrow());
        assertEquals(1, sharedRedis.size());
    }
}
