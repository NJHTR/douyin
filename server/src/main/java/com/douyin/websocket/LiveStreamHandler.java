package com.douyin.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.douyin.service.LiveHostPresenceService;
import com.douyin.service.LiveService;
import com.douyin.service.LivePresenceService;
import com.douyin.entity.LiveRoom;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/**
 * 直播控制 WebSocket（媒体不经过此通道）
 *   Broadcaster → ws://host/ws/live/{roomId}?role=host
 *   Viewer     → ws://host/ws/live/{roomId}?role=viewer
 *
 * 控制消息格式 (JSON):
 *   { "type": "chat", "userId": 123, "nickname": "...", "text": "..." }
 *   { "type": "like", "count": 1 }
 *   { "type": "end" }
 */
@Slf4j
@Component
public class LiveStreamHandler extends TextWebSocketHandler {

    private final LiveService liveService;
    private final LivePresenceService livePresenceService;
    private final WebSocketOutboundDispatcher outboundDispatcher;
    private final ObjectMapper objectMapper;
    private final LiveHostPresenceService hostPresenceService;
    private final LiveRoomClusterBus clusterBus;
    private final int endDelaySeconds;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "live-control-maintenance");
        thread.setDaemon(true);
        return thread;
    });

    /** roomId → Set<WebSocketSession> */
    private final ConcurrentHashMap<Long, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();
    /** sessionId → roomId */
    private final ConcurrentHashMap<String, Long> sessionRoom = new ConcurrentHashMap<>();
    /** sessionId → role (host/viewer) */
    private final ConcurrentHashMap<String, String> sessionRole = new ConcurrentHashMap<>();
    /** sessionId → userId (for host disconnect cleanup) */
    private final ConcurrentHashMap<String, Long> sessionUserId = new ConcurrentHashMap<>();
    /** sessionId → (room,user,client presence session) */
    private final ConcurrentHashMap<String, String> presenceSession = new ConcurrentHashMap<>();
    /** roomId → 延迟关播任务（主播断线 30s 后才真正关播，给重连留机会） */
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> pendingEnds = new ConcurrentHashMap<>();

    /** Compatibility constructor for direct unit-test/integration construction. */
    public LiveStreamHandler(LiveService liveService, LivePresenceService livePresenceService) {
        this(liveService, livePresenceService,
                new WebSocketOutboundDispatcher(8, 4096, 256), new ObjectMapper());
    }

    public LiveStreamHandler(LiveService liveService, LivePresenceService livePresenceService,
                             WebSocketOutboundDispatcher outboundDispatcher,
                             ObjectMapper objectMapper) {
        this(liveService, livePresenceService, outboundDispatcher, objectMapper, null, null, 30);
    }

    private LiveStreamHandler(LiveService liveService, LivePresenceService livePresenceService,
                              WebSocketOutboundDispatcher outboundDispatcher,
                              ObjectMapper objectMapper,
                              LiveHostPresenceService hostPresenceService,
                              LiveRoomClusterBus clusterBus,
                              int endDelaySeconds) {
        this.liveService = liveService;
        this.livePresenceService = livePresenceService;
        this.outboundDispatcher = outboundDispatcher;
        this.objectMapper = objectMapper;
        this.hostPresenceService = hostPresenceService;
        this.clusterBus = clusterBus;
        this.endDelaySeconds = Math.max(1, Math.min(endDelaySeconds, 300));
    }

    @Autowired
    public LiveStreamHandler(LiveService liveService, LivePresenceService livePresenceService,
                             WebSocketOutboundDispatcher outboundDispatcher,
                             ObjectMapper objectMapper,
                             LiveHostPresenceService hostPresenceService,
                             ObjectProvider<LiveRoomClusterBus> clusterBus,
                             @Value("${live.control.host-disconnect-grace-seconds:30}") int endDelaySeconds,
                             @Value("${live.control.host-lease-refresh-seconds:5}") int leaseRefreshSeconds) {
        this(liveService, livePresenceService, outboundDispatcher, objectMapper,
                hostPresenceService, clusterBus.getIfAvailable(), endDelaySeconds);
        int refreshSeconds = Math.max(2, Math.min(leaseRefreshSeconds, 15));
        scheduler.scheduleAtFixedRate(this::refreshHostLeases,
                refreshSeconds, refreshSeconds, TimeUnit.SECONDS);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long roomId = extractRoomId(session);
        String role = extractRole(session);
        Long userId = (Long) session.getAttributes().get("userId");
        if (roomId == null) {
            closeSession(session);
            return;
        }
        LiveRoom room = liveService.getById(roomId);
        if (room == null || ("viewer".equals(role) && !"LIVE".equals(room.getStatus()))
                || ("host".equals(role) && !isHostConnectableRoom(room))) {
            closeSession(session);
            return;
        }
        if (!"host".equals(role) && !"viewer".equals(role)) {
            closeSession(session);
            return;
        }
        // The query role is only a hint. A viewer cannot impersonate the
        // broadcaster, and a PREVIEW room cannot be used as a control bus.
        if ("host".equals(role) && (userId == null || !userId.equals(room.getHostUserId()))) {
            closeSession(session);
            return;
        }
        String clientSessionId = null;
        if ("viewer".equals(role)) {
            clientSessionId = extractPresenceSession(session);
            if (clientSessionId == null) {
                log.warn("Rejected live WS viewer without a valid presence session: roomId={}", roomId);
                closeSession(session);
                return;
            }
        }

        sessionRoom.put(session.getId(), roomId);
        sessionRole.put(session.getId(), role != null ? role : "viewer");

        // 记录 host 的 userId，用于断线时自动关播
        if (userId != null) {
            sessionUserId.put(session.getId(), userId);
        }

        if ("viewer".equals(role) && userId != null) {
            presenceSession.put(session.getId(), clientSessionId);
            if (livePresenceService.touch(roomId, userId, clientSessionId)) {
                liveService.joinRoom(roomId);
            }
        }

        rooms.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(session);
        outboundDispatcher.register(session);
        log.info("Live WS connected: roomId={}, role={}, viewers={}", roomId, role, viewerCount(roomId));

        // 主播重连 → 取消延迟关播任务
        if ("host".equals(role)) {
            if (hostPresenceService != null) hostPresenceService.touch(roomId, session.getId());
            ScheduledFuture<?> pending = pendingEnds.remove(roomId);
            if (pending != null) {
                pending.cancel(false);
                log.info("Host reconnected, cancelled auto-end for room {}", roomId);
            }
        }

        // 通知所有观众人数变化
        broadcastRoomStatus(roomId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long roomId = sessionRoom.get(session.getId());
        if (roomId == null) return;

        if (message.getPayloadLength() > 8_192) {
            log.warn("Rejected oversized live control payload: roomId={}, bytes={}", roomId,
                    message.getPayloadLength());
            return;
        }

        String payload = message.getPayload();
        String role = sessionRole.get(session.getId());
        try {
            var obj = objectMapper.readTree(payload);
            String type = obj.has("type") ? obj.get("type").asText() : "";
            // Media is never transported over the control WebSocket. Clients
            // must use SRS WHIP/WHEP; silently dropping legacy frame packets
            // prevents accidental base64/binary media fan-out.
            if ("frame".equals(type) || "media".equals(type)) return;
            if ("presence".equals(type)) {
                Long userId = sessionUserId.get(session.getId());
                String clientSessionId = presenceSession.get(session.getId());
                if (userId != null && clientSessionId != null) {
                    livePresenceService.touch(roomId, userId, clientSessionId);
                    broadcastRoomStatus(roomId);
                }
                return;
            }
            if ("host".equals(role) && hostPresenceService != null) {
                hostPresenceService.touch(roomId, session.getId());
            }
            if ("host".equals(role) && !"chat".equals(type)) return;
            if ("viewer".equals(role) && !"chat".equals(type) && !"like".equals(type)) return;
            if ("chat".equals(type)) {
                String text = obj.has("text") ? obj.get("text").asText("").trim() : "";
                if (text.isEmpty() || text.length() > 500) return;
            }
            if ("like".equals(type)) {
                int count = obj.has("count") ? obj.get("count").asInt(1) : 1;
                if (count < 1 || count > 5) return;
            }
        } catch (Exception ignored) {
            return;
        }

        // Chat/like are broadcast as control events only. The sender receives
        // the same event so all clients share one display projection; media
        // bytes never enter this loop.
        broadcastRoomEvent(roomId, payload, false);
    }

    /** Notify control clients that the provider room ended, then release sockets. */
    public void broadcastEnd(Long roomId) {
        broadcastRoomEvent(roomId, "{\"type\":\"end\"}", true);
    }

    /** Called by the Redis room subscriber; it must never publish again. */
    public void deliverClusterEvent(Long roomId, String payload, boolean closeAfter) {
        deliverLocal(roomId, payload, closeAfter);
    }

    private void broadcastRoomEvent(Long roomId, String payload, boolean closeAfter) {
        deliverLocal(roomId, payload, closeAfter);
        if (clusterBus != null) clusterBus.publish(roomId, payload, closeAfter);
    }

    private void deliverLocal(Long roomId, String payload, boolean closeAfter) {
        Set<WebSocketSession> set = rooms.get(roomId);
        if (set == null) return;
        for (WebSocketSession session : set.toArray(new WebSocketSession[0])) {
            if (!session.isOpen()) continue;
            if (closeAfter) {
                outboundDispatcher.sendAndClose(session, payload, CloseStatus.NORMAL);
            } else {
                outboundDispatcher.send(session, payload);
            }
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        // Deliberately no binary media path. Close only malformed/oversized
        // control traffic; normal clients should never send binary here.
        log.warn("Rejected binary live control payload: roomId={}, bytes={}",
                sessionRoom.get(session.getId()), message.getPayloadLength());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long roomId = sessionRoom.remove(session.getId());
        String role = sessionRole.remove(session.getId());
        Long userId = sessionUserId.remove(session.getId());

        if (roomId != null) {
            Set<WebSocketSession> set = rooms.get(roomId);
            if (set != null) {
                set.remove(session);
                if (set.isEmpty()) rooms.remove(roomId, set);
            }
        }
        if ("host".equals(role) && roomId != null && hostPresenceService != null) {
            hostPresenceService.leave(roomId, session.getId());
        }

        // 主播断线 → 延迟关播，给重连留机会；最终判断使用共享 lease。
        if ("host".equals(role) && roomId != null && userId != null) {
            ScheduledFuture<?> existing = pendingEnds.get(roomId);
            if (existing != null) {
                existing.cancel(false);
            }
            ScheduledFuture<?> future = scheduler.schedule(() -> {
                pendingEnds.remove(roomId);
                autoEndIfHostAbsent(roomId, userId);
            }, endDelaySeconds, TimeUnit.SECONDS);
            pendingEnds.put(roomId, future);
            log.info("Host disconnected, scheduling auto-end for room {} in {}s", roomId, endDelaySeconds);
        }

        if (roomId != null) {
            outboundDispatcher.unregister(session);
            // Presence is TTL based. Do not delete immediately here: a browser
            // reconnect may close the old socket after the new one is active.
            presenceSession.remove(session.getId());
            log.info("Live WS disconnected: roomId={}, viewers={}", roomId, viewerCount(roomId));
            broadcastRoomStatus(roomId);
        }
    }

    private void broadcastRoomStatus(Long roomId) {
        int count = viewerCount(roomId);
        String msg = "{\"type\":\"viewer_count\",\"count\":" + count + "}";
        broadcastRoomEvent(roomId, msg, false);
    }

    private void refreshHostLeases() {
        if (hostPresenceService == null) return;
        try {
            rooms.forEach((roomId, sessions) -> sessions.forEach(session -> {
                if (session.isOpen() && "host".equals(sessionRole.get(session.getId()))) {
                    hostPresenceService.touch(roomId, session.getId());
                }
            }));
        } catch (RuntimeException e) {
            log.warn("Failed to refresh local live host leases: {}", e.getMessage());
        }
    }

    private boolean hasLocalHost(Long roomId) {
        Set<WebSocketSession> sessions = rooms.get(roomId);
        return sessions != null && sessions.stream()
                .anyMatch(s -> s.isOpen() && "host".equals(sessionRole.get(s.getId())));
    }

    void autoEndIfHostAbsent(Long roomId, Long userId) {
        try {
            boolean hostOnline = hostPresenceService == null
                    ? hasLocalHost(roomId) : hostPresenceService.hasActiveHost(roomId);
            LiveRoom current = liveService.getById(roomId);
            if (hostOnline || !isActiveRoom(current)) return;
            String lockToken = hostPresenceService == null
                    ? "local" : hostPresenceService.tryAcquireEndLock(roomId);
            if (lockToken == null) return;
            try {
                if (hostPresenceService != null && hostPresenceService.hasActiveHost(roomId)) return;
                LiveRoom ended = liveService.endLive(roomId, userId);
                if (ended != null && ("ENDING".equals(ended.getStatus())
                        || "ENDED".equals(ended.getStatus()))) {
                    broadcastEnd(roomId);
                    log.info("Auto-ended live room {} after {}s delay, userId={}",
                            roomId, endDelaySeconds, userId);
                }
            } finally {
                if (hostPresenceService != null) {
                    hostPresenceService.releaseEndLock(roomId, lockToken);
                }
            }
        } catch (Exception e) {
            log.error("Failed to auto-end room {}: {}", roomId, e.getMessage());
        }
    }

    private static boolean isActiveRoom(LiveRoom room) {
        return room != null && ("STARTING".equals(room.getStatus())
                || "LIVE".equals(room.getStatus()) || "DEGRADED".equals(room.getStatus()));
    }

    private static boolean isHostConnectableRoom(LiveRoom room) {
        return room != null && ("PREVIEW".equals(room.getStatus()) || isActiveRoom(room));
    }

    private Long extractRoomId(WebSocketSession session) {
        String path = session.getUri() != null ? session.getUri().getPath() : "";
        try {
            // /ws/live/{roomId}
            String[] parts = path.split("/");
            return Long.parseLong(parts[parts.length - 1]);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractRole(WebSocketSession session) {
        String query = session.getUri() != null ? session.getUri().getQuery() : "";
        if (query == null) return "viewer";
        for (String param : query.split("&")) {
            String[] kv = param.split("=");
            if (kv.length == 2 && "role".equals(kv[0])) return kv[1];
        }
        return "viewer";
    }

    private String extractPresenceSession(WebSocketSession session) {
        String query = session.getUri() != null ? session.getUri().getQuery() : "";
        if (query != null) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && "sessionId".equals(kv[0]) && !kv[1].isBlank()) {
                    try {
                        String decoded = java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8);
                        String normalized = decoded.trim();
                        return normalized.isEmpty() || normalized.length() > 128 ? null : normalized;
                    } catch (IllegalArgumentException ignored) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    private int viewerCount(Long roomId) {
        return livePresenceService.count(roomId);
    }

    private void closeSession(WebSocketSession session) {
        try { session.close(); } catch (IOException ignored) {}
    }

    @PreDestroy
    public void shutdown() {
        pendingEnds.values().forEach(task -> task.cancel(false));
        pendingEnds.clear();
        scheduler.shutdownNow();
    }
}
