package com.douyin.websocket;

import com.douyin.rtc.service.CallReconciliationService;
import com.douyin.kafka.MessagePublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

class ChatWebSocketHandlerSecurityTest {

    @Test
    void unsupportedLegacyHangupCannotMutateOrPersistCall() throws Exception {
        MessagePublisher publisher = mock(MessagePublisher.class);
        SessionManager sessions = mock(SessionManager.class);
        WebSocketAuthorizationService acl = mock(WebSocketAuthorizationService.class);
        TestHandler handler = new TestHandler(
                publisher, sessions, new ObjectMapper(), mock(CallReconciliationService.class), acl);
        WebSocketSession socket = socket(1L);

        handler.handle(socket, "{\"type\":\"call_signal\",\"signal_type\":\"hangup\",\"call_id\":\"c1\",\"to_user_id\":2}");

        verifyNoInteractions(acl, sessions, publisher);
    }

    @Test
    void callRequestIsOnlyRelayedAfterParticipantChecks() throws Exception {
        MessagePublisher publisher = mock(MessagePublisher.class);
        SessionManager sessions = mock(SessionManager.class);
        WebSocketAuthorizationService acl = mock(WebSocketAuthorizationService.class);
        TestHandler handler = new TestHandler(
                publisher, sessions, new ObjectMapper(), mock(CallReconciliationService.class), acl);
        WebSocketSession socket = socket(1L);

        handler.handle(socket, "{\"type\":\"call_signal\",\"signal_type\":\"call_request\",\"call_id\":\"c1\",\"to_user_id\":2}");

        verify(acl).assertCallParticipant(1L, "c1");
        verify(acl).assertCallTargets(java.util.List.of(2L), "c1");
        verify(sessions).push(eq(2L), contains("\"call_id\":\"c1\""));
        verifyNoInteractions(publisher);
    }

    private static WebSocketSession socket(Long userId) {
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.getAttributes()).thenReturn(new java.util.HashMap<>(Map.of("userId", userId)));
        return socket;
    }

    private static class TestHandler extends ChatWebSocketHandler {
        TestHandler(MessagePublisher p, SessionManager s, ObjectMapper m,
                    CallReconciliationService r, WebSocketAuthorizationService a) {
            super(p, s, m, r, a);
        }
        void handle(WebSocketSession session, String json) throws Exception {
            handleTextMessage(session, new TextMessage(json));
        }
    }
}
