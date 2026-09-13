package com.douyin.service;

import com.douyin.kafka.MessagePublisher;
import com.douyin.kafka.dto.NotificationEvent;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Isolates notification submission from the common ForkJoinPool. Kafka mode
 * still gets durable outbox retries; direct mode is bounded so a slow push
 * cannot consume request or unrelated worker threads.
 */
@Slf4j
@Service
public class NotificationDispatchService {

    static final int QUEUE_CAPACITY = 1024;
    private final MessagePublisher publisher;
    private final boolean durablePublisher;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            2, 8, 60, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY),
            r -> {
                Thread thread = new Thread(r, "notification-dispatcher");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy());

    public NotificationDispatchService(
            MessagePublisher publisher,
            @Value("${douyin.kafka.enabled:false}") boolean durablePublisher) {
        this.publisher = publisher;
        this.durablePublisher = durablePublisher;
    }

    public boolean dispatch(NotificationEvent event) {
        if (event == null) return false;
        if (durablePublisher) {
            // Kafka mode writes the outbox row synchronously. Any enqueue
            // failure stays visible to the caller; successful rows are later
            // retried by EventOutboxDispatcher.
            publisher.publishNotification(event);
            return true;
        }
        try {
            executor.execute(() -> {
                try {
                    publisher.publishNotification(event);
                } catch (Exception e) {
                    log.error("通知派发失败: toUser={} type={} eventId={}",
                            event.getUserId(), event.getType(), event.getEventId(), e);
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            log.warn("通知派发队列已满，任务丢弃: toUser={} type={} queue={}",
                    event.getUserId(), event.getType(), executor.getQueue().size());
            return false;
        }
    }

    int queueSize() {
        return executor.getQueue().size();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
