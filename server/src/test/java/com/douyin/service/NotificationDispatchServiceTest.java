package com.douyin.service;

import com.douyin.kafka.MessagePublisher;
import com.douyin.kafka.dto.NotificationEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.timeout;

class NotificationDispatchServiceTest {

    @Test
    void dispatchUsesDedicatedWorkerAndPublishesNotification() {
        MessagePublisher publisher = mock(MessagePublisher.class);
        NotificationDispatchService service = new NotificationDispatchService(publisher, false);
        try {
            NotificationEvent event = new NotificationEvent(2L, 1L, 1,
                    null, null, "follow", System.currentTimeMillis(), "evt-1");
            org.junit.jupiter.api.Assertions.assertTrue(service.dispatch(event));
            verify(publisher, timeout(1000)).publishNotification(event);
        } finally {
            service.shutdown();
        }
    }

    @Test
    void durableModeWritesOutboxOnCallerThread() {
        MessagePublisher publisher = mock(MessagePublisher.class);
        NotificationDispatchService service = new NotificationDispatchService(publisher, true);
        try {
            NotificationEvent event = new NotificationEvent(2L, 1L, 1,
                    null, null, "follow", System.currentTimeMillis(), "evt-2");
            org.junit.jupiter.api.Assertions.assertTrue(service.dispatch(event));
            verify(publisher).publishNotification(event);
        } finally {
            service.shutdown();
        }
    }
}
