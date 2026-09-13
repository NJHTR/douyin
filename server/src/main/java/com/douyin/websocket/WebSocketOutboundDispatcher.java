package com.douyin.websocket;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Non-blocking, ordered WebSocket delivery with a bounded queue per connection.
 * Slow clients are disconnected instead of holding HTTP or Kafka consumer threads.
 */
@Slf4j
@Component
public class WebSocketOutboundDispatcher {

    private final Map<WebSocketSession, OutboundChannel> channels = new ConcurrentHashMap<>();
    private final Executor executor;
    private final ExecutorService ownedExecutor;
    private final int sessionQueueCapacity;

    @Autowired
    public WebSocketOutboundDispatcher(
            @Value("${websocket.outbound.worker-count:8}") int workerCount,
            @Value("${websocket.outbound.executor-queue-capacity:4096}") int executorQueueCapacity,
            @Value("${websocket.outbound.session-queue-capacity:256}") int sessionQueueCapacity) {
        this(createExecutor(workerCount, executorQueueCapacity), sessionQueueCapacity, true);
    }

    WebSocketOutboundDispatcher(Executor executor, int sessionQueueCapacity) {
        this(executor, sessionQueueCapacity, false);
    }

    private WebSocketOutboundDispatcher(Executor executor, int sessionQueueCapacity, boolean ownsExecutor) {
        this.executor = Objects.requireNonNull(executor, "executor");
        if (sessionQueueCapacity < 1) {
            throw new IllegalArgumentException("sessionQueueCapacity must be positive");
        }
        this.sessionQueueCapacity = sessionQueueCapacity;
        this.ownedExecutor = ownsExecutor ? (ExecutorService) executor : null;
    }

    public void register(WebSocketSession session) {
        if (session != null) {
            channels.compute(session, (ignored, current) ->
                    current == null || current.closed.get()
                            ? new OutboundChannel(session, sessionQueueCapacity)
                            : current);
        }
    }

    public void unregister(WebSocketSession session) {
        if (session == null) {
            return;
        }
        OutboundChannel channel = channels.remove(session);
        if (channel != null) {
            channel.closed.set(true);
            channel.queue.clear();
        }
    }

    /** Queues a message without waiting for a network write. */
    public boolean send(WebSocketSession session, String payload) {
        if (payload == null) {
            return false;
        }
        return enqueue(session, new OutboundCommand(new TextMessage(payload), null));
    }

    /** Queues a final message and closes the socket after that message is written. */
    public boolean sendAndClose(WebSocketSession session, String payload, CloseStatus closeStatus) {
        if (payload == null) {
            return false;
        }
        return enqueue(session, new OutboundCommand(
                new TextMessage(payload), Objects.requireNonNull(closeStatus, "closeStatus")));
    }

    private boolean enqueue(WebSocketSession session, OutboundCommand command) {
        if (session == null || command.message().getPayload() == null || !session.isOpen()) {
            unregister(session);
            return false;
        }
        OutboundChannel channel = channels.computeIfAbsent(session,
                ignored -> new OutboundChannel(session, sessionQueueCapacity));
        if (channel.closed.get()) {
            return false;
        }
        if (!channel.queue.offer(command)) {
            fail(channel, "outbound queue overflow", null);
            return false;
        }
        schedule(channel);
        return !channel.closed.get();
    }

    private OutboundChannel channelFor(WebSocketSession session) {
        return channels.computeIfAbsent(session,
                ignored -> new OutboundChannel(session, sessionQueueCapacity));
    }

    private void schedule(OutboundChannel channel) {
        if (channel.closed.get() || !channel.draining.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.execute(() -> drain(channel));
        } catch (RejectedExecutionException e) {
            channel.draining.set(false);
            fail(channel, "outbound executor saturated", e);
        }
    }

    private void drain(OutboundChannel channel) {
        try {
            OutboundCommand command;
            while (!channel.closed.get() && (command = channel.queue.poll()) != null) {
                if (!channel.session.isOpen()) {
                    unregister(channel.session);
                    return;
                }
                channel.session.sendMessage(command.message());
                if (command.closeAfter() != null) {
                    close(channel, command.closeAfter());
                    return;
                }
            }
        } catch (IOException | RuntimeException e) {
            fail(channel, "websocket delivery failed", e);
        } finally {
            channel.draining.set(false);
            if (!channel.closed.get() && !channel.queue.isEmpty()) {
                schedule(channel);
            }
        }
    }

    private void fail(OutboundChannel channel, String reason, Throwable error) {
        if (error == null) {
            log.warn("Closing slow WebSocket session: sessionId={}, reason={}",
                    sessionId(channel.session), reason);
        } else {
            log.warn("Closing failed WebSocket session: sessionId={}, reason={}",
                    sessionId(channel.session), reason, error);
        }
        close(channel, CloseStatus.SESSION_NOT_RELIABLE.withReason(reason));
    }

    private void close(OutboundChannel channel, CloseStatus status) {
        if (!channel.closed.compareAndSet(false, true)) {
            return;
        }
        channels.remove(channel.session, channel);
        channel.queue.clear();
        try {
            if (channel.session.isOpen()) {
                channel.session.close(status);
            }
        } catch (IOException e) {
            log.debug("Failed to close WebSocket session {}", sessionId(channel.session), e);
        }
    }

    private static ExecutorService createExecutor(int workerCount, int queueCapacity) {
        if (workerCount < 1 || queueCapacity < 1) {
            throw new IllegalArgumentException("WebSocket outbound executor limits must be positive");
        }
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "ws-outbound-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                workerCount,
                workerCount,
                30,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private static String sessionId(WebSocketSession session) {
        try {
            return session.getId();
        } catch (RuntimeException ignored) {
            return "unknown";
        }
    }

    @PreDestroy
    public void shutdown() {
        channels.values().forEach(channel -> {
            channel.closed.set(true);
            channel.queue.clear();
        });
        channels.clear();
        if (ownedExecutor != null) {
            ownedExecutor.shutdownNow();
        }
    }

    private record OutboundCommand(TextMessage message, CloseStatus closeAfter) {
    }

    private static final class OutboundChannel {
        private final WebSocketSession session;
        private final ArrayBlockingQueue<OutboundCommand> queue;
        private final AtomicBoolean draining = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();

        private OutboundChannel(WebSocketSession session, int queueCapacity) {
            this.session = session;
            this.queue = new ArrayBlockingQueue<>(queueCapacity);
        }
    }
}
