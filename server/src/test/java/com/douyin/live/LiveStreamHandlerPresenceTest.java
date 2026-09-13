package com.douyin.live;

import com.douyin.entity.LiveRoom;
import com.douyin.service.LivePresenceService;
import com.douyin.service.LiveService;
import com.douyin.websocket.LiveStreamHandler;
import com.douyin.websocket.WebSocketOutboundDispatcher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LiveStreamHandlerPresenceTest {

    @Test
    void viewerWithoutClientSessionIdIsRejectedBeforeItCanAffectPresence() throws Exception {
        LiveService liveService = mock(LiveService.class);
        LivePresenceService presenceService = mock(LivePresenceService.class);
        LiveStreamHandler handler = new LiveStreamHandler(liveService, presenceService);
        WebSocketSession session = mock(WebSocketSession.class);
        LiveRoom room = new LiveRoom();
        room.setId(100L);
        room.setStatus("LIVE");

        when(session.getId()).thenReturn("missing-presence-session");
        when(session.getUri()).thenReturn(URI.create("ws://localhost/ws/live/100?role=viewer"));
        when(session.getAttributes()).thenReturn(Map.of("userId", 7L));
        when(liveService.getById(100L)).thenReturn(room);

        handler.afterConnectionEstablished(session);

        verify(session).close();
        verifyNoInteractions(presenceService);
        verify(liveService, never()).joinRoom(100L);
    }

    @Test
    void viewerStatusBroadcastUsesBoundedOutboundDispatcher() throws Exception {
        LiveService liveService = mock(LiveService.class);
        LivePresenceService presenceService = mock(LivePresenceService.class);
        WebSocketOutboundDispatcher outbound = mock(WebSocketOutboundDispatcher.class);
        LiveStreamHandler handler = new LiveStreamHandler(
                liveService, presenceService, outbound, new ObjectMapper());
        WebSocketSession session = mock(WebSocketSession.class);
        LiveRoom room = new LiveRoom();
        room.setId(101L);
        room.setStatus("LIVE");

        when(session.getId()).thenReturn("viewer-status");
        when(session.isOpen()).thenReturn(true);
        when(session.getUri()).thenReturn(URI.create(
                "ws://localhost/ws/live/101?role=viewer&sessionId=client-101"));
        when(session.getAttributes()).thenReturn(Map.of("userId", 8L));
        when(liveService.getById(101L)).thenReturn(room);
        when(presenceService.touch(101L, 8L, "client-101")).thenReturn(true);
        when(presenceService.count(101L)).thenReturn(1);

        handler.afterConnectionEstablished(session);

        verify(outbound).register(session);
        verify(outbound).send(session, "{\"type\":\"viewer_count\",\"count\":1}");
        verify(session, never()).sendMessage(org.mockito.ArgumentMatchers.any());

        handler.afterConnectionClosed(session, org.springframework.web.socket.CloseStatus.NORMAL);
        verify(outbound).unregister(session);
    }
}
