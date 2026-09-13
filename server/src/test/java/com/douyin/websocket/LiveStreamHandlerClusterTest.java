package com.douyin.websocket;

import com.douyin.entity.LiveRoom;
import com.douyin.service.LiveHostPresenceService;
import com.douyin.service.LivePresenceService;
import com.douyin.service.LiveService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveStreamHandlerClusterTest {

    @Test
    void localSocketRegistriesAreIsolatedBetweenHandlerInstances() throws Exception {
        LiveService liveService = mock(LiveService.class);
        LivePresenceService presence = mock(LivePresenceService.class);
        WebSocketOutboundDispatcher outboundA = mock(WebSocketOutboundDispatcher.class);
        WebSocketOutboundDispatcher outboundB = mock(WebSocketOutboundDispatcher.class);
        LiveStreamHandler nodeA = new LiveStreamHandler(liveService, presence, outboundA, new ObjectMapper());
        LiveStreamHandler nodeB = new LiveStreamHandler(liveService, presence, outboundB, new ObjectMapper());
        LiveRoom room = liveRoom(50L, 1L, "LIVE");
        WebSocketSession socketA = viewerSocket("node-a-viewer", 50L, 2L);
        when(liveService.getById(50L)).thenReturn(room);
        when(presence.count(50L)).thenReturn(1);

        nodeA.afterConnectionEstablished(socketA);
        nodeB.deliverClusterEvent(50L, "remote", false);

        verify(outboundA).send(socketA, "{\"type\":\"viewer_count\",\"count\":1}");
        verifyNoSend(outboundB);
        nodeA.shutdown();
        nodeB.shutdown();
    }

    @Test
    void hostOnAnotherNodePreventsDisconnectWorkerFromEndingRoom() throws Exception {
        LiveService liveService = mock(LiveService.class);
        LivePresenceService presence = mock(LivePresenceService.class);
        LiveHostPresenceService hostPresence = mock(LiveHostPresenceService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<LiveRoomClusterBus> busProvider = mock(ObjectProvider.class);
        when(busProvider.getIfAvailable()).thenReturn(null);
        LiveStreamHandler nodeA = new LiveStreamHandler(liveService, presence,
                mock(WebSocketOutboundDispatcher.class), new ObjectMapper(), hostPresence,
                busProvider, 30, 15);
        LiveRoom room = liveRoom(51L, 7L, "LIVE");
        WebSocketSession hostA = hostSocket("host-a", 51L, 7L);
        when(liveService.getById(51L)).thenReturn(room);
        when(hostPresence.hasActiveHost(51L)).thenReturn(true);

        nodeA.afterConnectionEstablished(hostA);
        nodeA.afterConnectionClosed(hostA, CloseStatus.NORMAL);
        nodeA.autoEndIfHostAbsent(51L, 7L);

        verify(hostPresence).touch(51L, "host-a");
        verify(hostPresence).leave(51L, "host-a");
        verify(liveService, never()).endLive(any(), any());
        nodeA.shutdown();
    }

    @Test
    void endEventClosesSocketsOwnedByBothNodes() throws Exception {
        LiveService liveService = mock(LiveService.class);
        LivePresenceService presence = mock(LivePresenceService.class);
        LiveHostPresenceService hostPresence = mock(LiveHostPresenceService.class);
        LiveRoomClusterBus bus = mock(LiveRoomClusterBus.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<LiveRoomClusterBus> nodeABus = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<LiveRoomClusterBus> noBus = mock(ObjectProvider.class);
        when(nodeABus.getIfAvailable()).thenReturn(bus);
        when(noBus.getIfAvailable()).thenReturn(null);
        WebSocketOutboundDispatcher outboundA = mock(WebSocketOutboundDispatcher.class);
        WebSocketOutboundDispatcher outboundB = mock(WebSocketOutboundDispatcher.class);
        LiveStreamHandler nodeA = new LiveStreamHandler(liveService, presence, outboundA,
                new ObjectMapper(), hostPresence, nodeABus, 30, 15);
        LiveStreamHandler nodeB = new LiveStreamHandler(liveService, presence, outboundB,
                new ObjectMapper(), hostPresence, noBus, 30, 15);
        LiveRoom room = liveRoom(52L, 1L, "LIVE");
        WebSocketSession socketA = viewerSocket("viewer-a", 52L, 2L);
        WebSocketSession socketB = viewerSocket("viewer-b", 52L, 3L);
        when(liveService.getById(52L)).thenReturn(room);

        nodeA.afterConnectionEstablished(socketA);
        nodeB.afterConnectionEstablished(socketB);
        nodeA.broadcastEnd(52L);
        nodeB.deliverClusterEvent(52L, "{\"type\":\"end\"}", true);

        verify(outboundA).sendAndClose(socketA, "{\"type\":\"end\"}", CloseStatus.NORMAL);
        verify(outboundB).sendAndClose(socketB, "{\"type\":\"end\"}", CloseStatus.NORMAL);
        verify(bus).publish(52L, "{\"type\":\"end\"}", true);
        nodeA.shutdown();
        nodeB.shutdown();
    }

    private static void verifyNoSend(WebSocketOutboundDispatcher outbound) {
        verify(outbound, never()).send(any(), any());
        verify(outbound, never()).sendAndClose(any(), any(), any());
    }

    private static LiveRoom liveRoom(Long roomId, Long hostUserId, String status) {
        LiveRoom room = new LiveRoom();
        room.setId(roomId);
        room.setHostUserId(hostUserId);
        room.setStatus(status);
        return room;
    }

    private static WebSocketSession viewerSocket(String id, Long roomId, Long userId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        when(session.getUri()).thenReturn(URI.create(
                "ws://localhost/ws/live/" + roomId + "?role=viewer&sessionId=" + id));
        when(session.getAttributes()).thenReturn(Map.of("userId", userId));
        return session;
    }

    private static WebSocketSession hostSocket(String id, Long roomId, Long userId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        when(session.getUri()).thenReturn(URI.create("ws://localhost/ws/live/" + roomId + "?role=host"));
        when(session.getAttributes()).thenReturn(Map.of("userId", userId));
        return session;
    }
}
