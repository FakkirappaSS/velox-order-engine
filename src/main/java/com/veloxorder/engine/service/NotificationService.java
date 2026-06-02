package com.veloxorder.engine.service;

import com.veloxorder.engine.entity.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class NotificationService {

    @Async("orderTaskExecutor")
    public CompletableFuture<Void> sendNotification(Order order) {
        log.info("[{}] Dispatching email/warehouse notification for Order ID: {}",
                Thread.currentThread().getName(), order.getId());

        try {
            // Simulate 200ms notification network delay
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Notification thread was interrupted for Order {}", order.getId());
        }

        log.info("[{}] Notification sent successfully for Order ID: {}", Thread.currentThread().getName(), order.getId());
        return CompletableFuture.completedFuture(null);
    }
}
