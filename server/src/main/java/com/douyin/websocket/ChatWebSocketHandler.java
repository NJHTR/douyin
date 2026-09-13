package com.douyin.websocket;

import com.douyin.kafka.MessagePublisher;
import com.douyin.kafka.dto.ChatMessageEvent;
import com.douyin.kafka.dto.GroupMessageEvent;
import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.service.CallReconciliationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** WebSocket chat ingress; RTC signals are notification-only. */
@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final MessagePublisher messagePublisher;
    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper;
    private final CallReconciliationService callReconciliationService;
    private final WebSocketAuthorizationService authorizationService;

    public ChatWebSocketHandler(MessagePublisher messagePublisher, SessionManager sessionManager,
                                ObjectMapper objectMapper,
                                CallReconciliationService callReconciliationService,
                                WebSocketAuthorizationService authorizationService) {
        this.messagePublisher = messagePublisher;
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
        this.callReconciliationService = callReconciliationService;
        this.authorizationService = authorizationService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            sessionManager.register(userId, session);
            callReconciliationService.reconcileSession(userId, session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) {
        Long fromUserId = (Long) session.getAttributes().get("userId");
        if (fromUserId == null) return;

        Map<String, Object> payload;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(textMessage.getPayload(), Map.class);
            payload = parsed;
        } catch (Exception e) {
            log.warn("Invalid WebSocket message: {}", e.getMessage());
            return;
        }

        String type = String.valueOf(payload.getOrDefault("type", ""));
        if ("call_signal".equals(type)) {
            handleCallNotification(fromUserId, payload);
            return;
        }

        Long toUserId = longValue(payload.get("to_user_id"));
        Long groupId = longValue(payload.get("group_id"));
        String content = String.valueOf(payload.getOrDefault("content", ""));
        Integer msgType = integerValue(payload.get("msg_type"), 1);
        String extra = String.valueOf(payload.getOrDefault("extra", ""));

        if (groupId != null) {
            try {
                authorizationService.assertGroupMember(fromUserId, groupId);
            } catch (CallDomainException e) {
                log.warn("Rejected unauthorized group message: userId={} groupId={}", fromUserId, groupId);
                return;
            }
            GroupMessageEvent event = new GroupMessageEvent();
            event.setGroupId(groupId);
            event.setFromUserId(fromUserId);
            event.setContent(content);
            event.setMsgType(msgType);
            event.setExtra(extra);
            event.setTimestamp(System.currentTimeMillis());
            messagePublisher.publishGroupChat(event);
            return;
        }

        if (toUserId == null) return;
        try {
            authorizationService.assertDirectMessageAllowed(fromUserId, toUserId);
        } catch (CallDomainException e) {
            log.warn("Rejected unauthorized direct message: from={} to={}", fromUserId, toUserId);
            return;
        }

        ChatMessageEvent event = new ChatMessageEvent();
        event.setFromUserId(fromUserId);
        event.setToUserId(toUserId);
        event.setContent(content);
        event.setMsgType(msgType);
        event.setExtra(extra);
        event.setTimestamp(System.currentTimeMillis());
        messagePublisher.publishChat(event);
    }

    /** Legacy call_signal can only deliver an authenticated notification. */
    private void handleCallNotification(Long fromUserId, Map<String, Object> payload) {
        String signalType = String.valueOf(payload.getOrDefault("signal_type", ""));
        if (!("call_request".equals(signalType)
                || "call_busy".equals(signalType)
                || "call_cancelled".equals(signalType)
                || "participant_update".equals(signalType))) {
            log.warn("Rejected unsupported legacy call signal: userId={} signalType={}",
                    fromUserId, signalType);
            return;
        }

        String callId = extractCallId(payload);
        List<Long> targetIds = parseTargetIds(payload);
        try {
            authorizationService.assertCallParticipant(fromUserId, callId);
            authorizationService.assertCallTargets(targetIds, callId);
        } catch (CallDomainException e) {
            log.warn("Rejected unauthorized call signal: userId={} callId={} reason={}",
                    fromUserId, callId, e.getMessage());
            return;
        }

        Map<String, Object> signal = new LinkedHashMap<>();
        signal.put("type", "call_signal");
        signal.put("from_user_id", String.valueOf(fromUserId));
        signal.put("signal_type", signalType);
        signal.put("call_id", callId);
        signal.put("data", payload.getOrDefault("data", null));
        try {
            String json = objectMapper.writeValueAsString(signal);
            for (Long targetId : targetIds) sessionManager.push(targetId, json);
        } catch (Exception e) {
            log.error("Failed to relay call notification", e);
        }
    }

    private static List<Long> parseTargetIds(Map<String, Object> payload) {
        Set<Long> ids = new LinkedHashSet<>();
        addTarget(ids, payload.get("to_user_id"));
        Object raw = payload.get("to_user_ids");
        if (raw != null) {
            for (String value : String.valueOf(raw).split(",")) addTarget(ids, value.trim());
        }
        return new ArrayList<>(ids);
    }

    private static void addTarget(Set<Long> ids, Object raw) {
        if (raw == null) return;
        try {
            long id = Long.parseLong(String.valueOf(raw));
            if (id > 0) ids.add(id);
        } catch (NumberFormatException ignored) {
            // The empty target set is rejected by the centralized ACL.
        }
    }

    private static String extractCallId(Map<String, Object> payload) {
        Object direct = payload.get("call_id");
        if (direct != null && !String.valueOf(direct).isBlank()) return String.valueOf(direct);
        Object data = payload.get("data");
        if (data instanceof Map<?, ?> map && map.get("call_id") != null) {
            return String.valueOf(map.get("call_id"));
        }
        return null;
    }

    private static Long longValue(Object value) {
        if (value == null) return null;
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Integer integerValue(Object value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) sessionManager.unregister(userId, session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket error: userId={}", session.getAttributes().get("userId"), exception);
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) sessionManager.unregister(userId, session);
    }
}
