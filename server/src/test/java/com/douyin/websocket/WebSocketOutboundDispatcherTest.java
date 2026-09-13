package com.douyin.websocket;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketOutboundDispatcherTest {

    @Test
    void queuesWritesOffTheCallerThreadAndPreservesConnectionOrder() throws Exception {
        QueuedExecutor executor = new QueuedExecutor();
        WebSocketOutboundDispatcher dispatcher = new WebSocketOutboundDispatcher(executor, 4);
        WebSocketSession session = openSession("ordered");

        assertTrue(dispatcher.send(session, "first"));
        assertTrue(dispatcher.send(session, "second"));
        verify(session, never()).sendMessage(any());

        executor.runNext();

        ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, org.mockito.Mockito.times(2)).sendMessage(messages.capture());
        assertEquals("first", messages.getAllValues().get(0).getPayload());
        assertEquals("second", messages.getAllValues().get(1).getPayload());
    }

    @Test
    void disconnectsAConnectionWhoseOutboundQueueIsFull() throws Exception {
        QueuedExecutor executor = new QueuedExecutor();
        WebSocketOutboundDispatcher dispatcher = new WebSocketOutboundDispatcher(executor, 2);
        WebSocketSession session = openSession("slow");

        assertTrue(dispatcher.send(session, "one"));
        assertTrue(dispatcher.send(session, "two"));
        assertFalse(dispatcher.send(session, "three"));

        verify(session).close(CloseStatus.SESSION_NOT_RELIABLE.withReason("outbound queue overflow"));
        executor.runNext();
        verify(session, never()).sendMessage(any());
    }

    @Test
    void finalMessageClosesOnlyAfterItHasBeenWritten() throws Exception {
        QueuedExecutor executor = new QueuedExecutor();
        WebSocketOutboundDispatcher dispatcher = new WebSocketOutboundDispatcher(executor, 2);
        WebSocketSession session = openSession("ending");

        assertTrue(dispatcher.sendAndClose(session, "end", CloseStatus.NORMAL));
        verify(session, never()).close(any());

        executor.runNext();

        verify(session).sendMessage(new TextMessage("end"));
        verify(session).close(CloseStatus.NORMAL);
    }

    private static WebSocketSession openSession(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private static final class QueuedExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        private void runNext() {
            Runnable task = tasks.remove();
            task.run();
        }
    }
}
