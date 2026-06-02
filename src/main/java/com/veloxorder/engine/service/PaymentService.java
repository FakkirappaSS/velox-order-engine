package com.veloxorder.engine.service;

import com.veloxorder.engine.entity.Order;
import com.veloxorder.engine.exception.PaymentProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class PaymentService {

    @Async("orderTaskExecutor")
    public CompletableFuture<Void> processPayment(Order order) {
        log.info("[{}] Processing payment of ${} for Order ID: {} (Customer: {})",
                Thread.currentThread().getName(), order.getTotalAmount(), order.getId(), order.getCustomerId());

        try {
            // Simulate 1.5-second external payment gateway latency
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentProcessingException("Payment processing interrupted for Order " + order.getId());
        }

        // Simulating specific failure payloads for testing
        if ("FAIL_PAYMENT".equalsIgnoreCase(order.getCustomerId())) {
            log.error("[{}] Payment failed for Order ID: {}", Thread.currentThread().getName(), order.getId());
            throw new PaymentProcessingException("Payment processing failed: Transaction declined by bank");
        }

        log.info("[{}] Payment processed successfully for Order ID: {}", Thread.currentThread().getName(), order.getId());
        return CompletableFuture.completedFuture(null);
    }
}
