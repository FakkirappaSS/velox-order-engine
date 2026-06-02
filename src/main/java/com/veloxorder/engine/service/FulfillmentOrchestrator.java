package com.veloxorder.engine.service;

import com.veloxorder.engine.entity.Order;
import com.veloxorder.engine.entity.OrderItem;
import com.veloxorder.engine.entity.OrderStatus;
import com.veloxorder.engine.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class FulfillmentOrchestrator {

    private final PhysicalStockDeductionService physicalStockDeductionService;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final OrderRepository orderRepository;

    /**
     * Executes the background execution pipeline.
     * Starts three parallel tasks (Thread A, B, and C) and coordinates them.
     */
    @Async("orderTaskExecutor")
    public void fulfillOrder(Order order) {
        log.info("[{}] Orchestrating background fulfillment for Order ID: {} (Tracking ID: {})",
                Thread.currentThread().getName(), order.getId(), order.getOrderTrackingId());

        CompletableFuture<Void> threadA = deductPhysicalStockAsync(order);
        CompletableFuture<Void> threadB = paymentService.processPayment(order);
        CompletableFuture<Void> threadC = notificationService.sendNotification(order);

        CompletableFuture.allOf(threadA, threadB, threadC)
            .handle((result, throwable) -> {
                if (throwable != null) {
                    log.error("[{}] Fulfillment pipeline failed for Order ID: {}. Error: {}",
                            Thread.currentThread().getName(), order.getId(), throwable.getCause() != null ? throwable.getCause().getMessage() : throwable.getMessage());
                    updateOrderStatus(order.getId(), OrderStatus.FAILED);
                } else {
                    log.info("[{}] Fulfillment pipeline completed successfully for Order ID: {}",
                            Thread.currentThread().getName(), order.getId());
                    updateOrderStatus(order.getId(), OrderStatus.SUCCESS);
                }
                return null;
            });
    }

    /**
     * Thread A: Deducts physical stock in database with retry logic for optimistic locks.
     */
    @Async("orderTaskExecutor")
    public CompletableFuture<Void> deductPhysicalStockAsync(Order order) {
        log.info("[{}] Thread A starting stock deduction for Order ID: {}",
                Thread.currentThread().getName(), order.getId());

        for (OrderItem item : order.getItems()) {
            String sku = item.getProduct().getSku();
            int quantity = item.getQuantity();

            int attempt = 0;
            int maxRetries = 3;
            while (true) {
                try {
                    attempt++;
                    physicalStockDeductionService.deductStock(sku, quantity);
                    break; // Success, go to next item
                } catch (ObjectOptimisticLockingFailureException e) {
                    log.warn("[{}] Optimistic locking failure for SKU {} on attempt {}. Retrying...",
                            Thread.currentThread().getName(), sku, attempt);
                    if (attempt >= maxRetries) {
                        throw new RuntimeException("Failed to deduct physical stock for SKU " + sku +
                                " after " + maxRetries + " attempts due to concurrent updates.");
                    }
                    try {
                        // Random exponential backoff with jitter
                        Thread.sleep(50 + (int)(Math.random() * 100));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry wait interrupted for SKU " + sku, ie);
                    }
                }
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Helper transaction method to write status update to DB in a new transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateOrderStatus(Long orderId, OrderStatus status) {
        orderRepository.findById(orderId).ifPresentOrElse(
            order -> {
                order.setStatus(status);
                orderRepository.saveAndFlush(order);
                log.info("[{}] Updated database Order ID: {} status to: {}",
                        Thread.currentThread().getName(), orderId, status);
            },
            () -> log.error("Order ID {} not found for updating status to {}", orderId, status)
        );
    }
}
