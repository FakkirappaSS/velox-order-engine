package com.veloxorder.engine;

import com.veloxorder.engine.dto.OrderItemDto;
import com.veloxorder.engine.dto.OrderRequest;
import com.veloxorder.engine.entity.Order;
import com.veloxorder.engine.entity.OrderStatus;
import com.veloxorder.engine.entity.Product;
import com.veloxorder.engine.exception.InsufficientStockException;
import com.veloxorder.engine.repository.OrderRepository;
import com.veloxorder.engine.repository.ProductRepository;
import com.veloxorder.engine.service.InventoryCacheService;
import com.veloxorder.engine.service.OrderProcessingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class OrderProcessingConcurrencyTest {

    @Autowired
    private OrderProcessingService orderProcessingService;

    @Autowired
    private InventoryCacheService inventoryCacheService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    private static final String TEST_SKU = "CONC-TEST";

    @BeforeEach
    public void setup() {
        orderRepository.deleteAll();
        productRepository.deleteAll();

        // Create a single product with 10 units in stock
        Product product = Product.builder()
                .sku(TEST_SKU)
                .name("Concurrency Test Product")
                .price(new BigDecimal("10.00"))
                .stockQuantity(10)
                .build();
        productRepository.saveAndFlush(product);

        // Initialize cache
        inventoryCacheService.initializeCache();
    }

    @Test
    public void testConcurrentCheckouts_NoOverselling() throws InterruptedException, ExecutionException {
        int threadCount = 15; // 15 parallel checkouts for 10 items
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        AtomicInteger successRequests = new AtomicInteger(0);
        AtomicInteger failedRequests = new AtomicInteger(0);

        List<Future<Order>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final String customerId = "CUST-" + i;
            futures.add(executor.submit(() -> {
                try {
                    // Align threads to start simultaneously
                    startLatch.await();
                    OrderRequest request = OrderRequest.builder()
                            .customerId(customerId)
                            .items(List.of(
                                    OrderItemDto.builder()
                                            .sku(TEST_SKU)
                                            .quantity(1)
                                            .build()
                            ))
                            .build();

                    Order order = orderProcessingService.processOrder(request);
                    successRequests.incrementAndGet();
                    return order;
                } catch (InsufficientStockException ex) {
                    failedRequests.incrementAndGet();
                    throw ex;
                } catch (Exception ex) {
                    failedRequests.incrementAndGet();
                    throw new RuntimeException(ex);
                } finally {
                    endLatch.countDown();
                }
            }));
        }

        // Trigger all threads at the same time
        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        // Verify counts
        assertEquals(10, successRequests.get(), "Exactly 10 requests should succeed.");
        assertEquals(5, failedRequests.get(), "Exactly 5 requests should fail due to lack of stock.");

        // Wait a few seconds for background threads (Thread A, B, C) to resolve
        // Payment has 1.5s delay, so let's wait 3.5s to ensure everything updates in DB.
        Thread.sleep(3500);

        // Verify cache stock is 0
        assertEquals(0, inventoryCacheService.getStock(TEST_SKU), "Cache stock should be empty.");

        // Verify DB physical stock is 0
        Product product = productRepository.findBySku(TEST_SKU).orElseThrow();
        assertEquals(0, product.getStockQuantity(), "DB physical stock should be empty.");

        // Verify status counts of order items in DB
        long successOrders = orderRepository.findAll().stream()
                .filter(o -> o.getStatus() == OrderStatus.SUCCESS)
                .count();
        assertEquals(10, successOrders, "All 10 accepted orders should transition to SUCCESS.");
    }
}
