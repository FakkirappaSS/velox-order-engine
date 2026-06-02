package com.veloxorder.engine.service;

import com.veloxorder.engine.dto.OrderItemDto;
import com.veloxorder.engine.dto.OrderRequest;
import com.veloxorder.engine.entity.Order;
import com.veloxorder.engine.entity.OrderItem;
import com.veloxorder.engine.entity.OrderStatus;
import com.veloxorder.engine.entity.Product;
import com.veloxorder.engine.exception.InsufficientStockException;
import com.veloxorder.engine.exception.ProductNotFoundException;
import com.veloxorder.engine.repository.OrderRepository;
import com.veloxorder.engine.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderProcessingService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final InventoryCacheService inventoryCacheService;
    private final FulfillmentOrchestrator fulfillmentOrchestrator;

    /**
     * Process an incoming order request.
     * Synchronous phase validates stock levels in-memory, saves Order as PENDING,
     * and spawns background tasks.
     */
    @Transactional
    public Order processOrder(OrderRequest request) {
        log.info("Received order request for Customer: {}", request.getCustomerId());

        // 1. Filter out payloads with invalid product SKUs or quantities less than or equal to 0 (Java 8 Streams)
        List<OrderItemDto> filteredItems = request.getItems().stream()
                .filter(item -> item.getSku() != null && !item.getSku().trim().isEmpty())
                .filter(item -> item.getQuantity() != null && item.getQuantity() > 0)
                .collect(Collectors.toList());

        if (filteredItems.isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item with valid SKU and quantity > 0");
        }

        // 2. Validate in-memory cache stock levels synchronously and perform atomic decrement
        List<String> decrementedSkus = new ArrayList<>();
        try {
            for (OrderItemDto item : filteredItems) {
                String sku = item.getSku();
                int qty = item.getQuantity();

                boolean success = inventoryCacheService.decrementStock(sku, qty);
                if (!success) {
                    throw new InsufficientStockException("Insufficient stock in cache for product SKU: " + sku);
                }
                decrementedSkus.add(sku);
            }
        } catch (Exception e) {
            // Rollback in-memory cache decrements if checkout fails mid-way
            log.warn("Order checkout failed validation. Reverting cache changes for: {}", decrementedSkus);
            for (int i = 0; i < decrementedSkus.size(); i++) {
                String sku = decrementedSkus.get(i);
                int qty = filteredItems.get(i).getQuantity();
                Integer current = inventoryCacheService.getStock(sku);
                inventoryCacheService.putStock(sku, (current != null ? current : 0) + qty);
            }
            throw e;
        }

        // 3. Map incoming Request DTOs to internal Database Entities (Java 8 Streams & Lambdas)
        List<OrderItem> dbItems = filteredItems.stream()
                .map(itemDto -> {
                    Product product = productRepository.findBySku(itemDto.getSku())
                            .orElseThrow(() -> new ProductNotFoundException("Product not found for SKU: " + itemDto.getSku()));
                    
                    return OrderItem.builder()
                            .product(product)
                            .quantity(itemDto.getQuantity())
                            .price(product.getPrice())
                            .build();
                })
                .collect(Collectors.toList());

        // 4. Calculate total order value using streams .reduce()
        BigDecimal totalAmount = dbItems.stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 5. Build order and save to DB
        Order order = Order.builder()
                .orderTrackingId(UUID.randomUUID().toString())
                .customerId(request.getCustomerId())
                .totalAmount(totalAmount)
                .status(OrderStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        // Establish bidirectional mapping
        dbItems.forEach(order::addItem);

        Order savedOrder = orderRepository.save(order);
        log.info("Order successfully registered as PENDING. Order ID: {}, Tracking ID: {}",
                savedOrder.getId(), savedOrder.getOrderTrackingId());

        // 6. Hand off heavy lifting asynchronously
        fulfillmentOrchestrator.fulfillOrder(savedOrder);

        return savedOrder;
    }
}
