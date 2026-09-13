package com.douyin.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shared host connection leases used to guard disconnect-driven live-room shutdown. */
@Slf4j
@Service
public class LiveHostPresenceService {

    private static final String HOST_KEY_PREFIX = "douyin:live:host-presence:";
    private static final String END_LOCK_PREFIX = "douyin:live:auto-end-lock:";
    private static final DefaultRedisScript<Long> TOUCH_SCRIPT = new DefaultRedisScript<>("""
            local now = tonumber(ARGV[1])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now)
            redis.call('ZADD', KEYS[1], ARGV[2], ARGV[3])
            redis.call('PEXPIRE', KEYS[1], ARGV[4])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> LEAVE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('ZREM', KEYS[1], ARGV[1])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[2])
            if redis.call('ZCARD', KEYS[1]) == 0 then
                redis.call('DEL', KEYS[1])
            end
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> ACTIVE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
            local count = redis.call('ZCARD', KEYS[1])
            if count == 0 then redis.call('DEL', KEYS[1]) end
            return count
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final long leaseMillis;
    private final AtomicBoolean redisWarningLogged = new AtomicBoolean();

    public LiveHostPresenceService(StringRedisTemplate redis,
            @Value("${live.control.host-lease-seconds:20}") long leaseSeconds) {
        this.redis = redis;
        this.leaseMillis = Duration.ofSeconds(Math.max(10, Math.min(leaseSeconds, 60))).toMillis();
    }

    public void touch(Long roomId, String connectionId) {
        if (roomId == null || connectionId == null || connectionId.isBlank()) return;
        long now = System.currentTimeMillis();
        try {
            redis.execute(TOUCH_SCRIPT, List.of(hostKey(roomId)), String.valueOf(now),
                    String.valueOf(now + leaseMillis), connectionId, String.valueOf(leaseMillis * 2));
        } catch (RuntimeException e) {
            warnRedis(e);
        }
    }

    public void leave(Long roomId, String connectionId) {
        if (roomId == null || connectionId == null || connectionId.isBlank()) return;
        try {
            redis.execute(LEAVE_SCRIPT, List.of(hostKey(roomId)), connectionId,
                    String.valueOf(System.currentTimeMillis()));
        } catch (RuntimeException e) {
            warnRedis(e);
        }
    }

    /** Redis failure is treated as online so an infrastructure outage cannot end a valid broadcast. */
    public boolean hasActiveHost(Long roomId) {
        if (roomId == null) return true;
        try {
            Long count = redis.execute(ACTIVE_SCRIPT, List.of(hostKey(roomId)),
                    String.valueOf(System.currentTimeMillis()));
            return count != null && count > 0;
        } catch (RuntimeException e) {
            warnRedis(e);
            return true;
        }
    }

    public String tryAcquireEndLock(Long roomId) {
        if (roomId == null) return null;
        String token = UUID.randomUUID().toString();
        try {
            Boolean acquired = redis.opsForValue().setIfAbsent(
                    END_LOCK_PREFIX + roomId, token, Duration.ofSeconds(15));
            return Boolean.TRUE.equals(acquired) ? token : null;
        } catch (RuntimeException e) {
            warnRedis(e);
            return null;
        }
    }

    public void releaseEndLock(Long roomId, String token) {
        if (roomId == null || token == null) return;
        try {
            redis.execute(RELEASE_LOCK_SCRIPT, List.of(END_LOCK_PREFIX + roomId), token);
        } catch (RuntimeException e) {
            warnRedis(e);
        }
    }

    private static String hostKey(Long roomId) {
        return HOST_KEY_PREFIX + roomId;
    }

    private void warnRedis(RuntimeException e) {
        if (redisWarningLogged.compareAndSet(false, true)) {
            log.warn("Live host presence Redis unavailable; automatic room ending is suspended: {}", e.getMessage());
        }
    }
}
