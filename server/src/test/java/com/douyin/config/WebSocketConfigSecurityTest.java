package com.douyin.config;

import com.douyin.websocket.ChatWebSocketHandler;
import com.douyin.websocket.DashboardWebSocketHandler;
import com.douyin.websocket.LiveStreamHandler;
import com.douyin.websocket.WebSocketHandshakeInterceptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class WebSocketConfigSecurityTest {

    @Test
    void productionWildcardOriginIsRejectedAtStartup() {
        assertThrows(IllegalStateException.class, () -> new WebSocketConfig(
                mock(ChatWebSocketHandler.class),
                mock(LiveStreamHandler.class),
                mock(DashboardWebSocketHandler.class),
                mock(WebSocketHandshakeInterceptor.class),
                "*",
                "prod"));
    }
}
