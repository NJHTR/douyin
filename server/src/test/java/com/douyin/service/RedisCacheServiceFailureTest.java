package com.douyin.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class RedisCacheServiceFailureTest {

    @Test
    void idempotencyRedisFailureIsNotTreatedAsFirstRequest() {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), any(), any(Duration.class)))
                .thenThrow(new RuntimeException("redis down"));

        RedisCacheService service = new RedisCacheService(redis);
        assertThrows(RedisCoordinationUnavailableException.class,
                () -> service.tryAcquireIdempotent("payment-1"));
    }

    @Test
    void lockRedisFailureDoesNotReturnFakeToken() {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), any(), any(Duration.class)))
                .thenThrow(new RuntimeException("redis down"));

        RedisCacheService service = new RedisCacheService(redis);
        assertThrows(RedisCoordinationUnavailableException.class,
                () -> service.tryLock("call:1", Duration.ofSeconds(10)));
    }

    @Test
    void rateLimitRedisFailureFailsClosed() {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        when(redis.execute(any(), anyList(), any(), any())).thenThrow(new RuntimeException("redis down"));

        RedisCacheService service = new RedisCacheService(redis);
        org.junit.jupiter.api.Assertions.assertFalse(
                service.rateLimit("call:create", "1", 20, Duration.ofMinutes(1)));
    }
}
