package com.veloxorder.engine.controller;

import com.veloxorder.engine.dto.ApiResponse;
import com.veloxorder.engine.dto.OrderRequest;
import com.veloxorder.engine.entity.Order;
import com.veloxorder.engine.entity.OrderStatus;
import com.veloxorder.engine.entity.Product;
import com.veloxorder.engine.repository.OrderRepository;
import com.veloxorder.engine.repository.ProductRepository;
import com.veloxorder.engine.service.InventoryCacheService;
import com.veloxorder.engine.service.OrderProcessingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class OrderController {

    private final OrderProcessingService orderProcessingService;
    private final InventoryCacheService inventoryCacheService;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ThreadPoolTaskExecutor orderTaskExecutor;

    /**
     * Endpoint to submit a new checkout request.
     * Returns immediately with PENDING order status and a tracking ID.
     */
    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkout(@Valid @RequestBody OrderRequest request) {
        Order order = orderProcessingService.processOrder(request);

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("orderId", order.getId());
        responseData.put("orderTrackingId", order.getOrderTrackingId());
        responseData.put("status", order.getStatus());
        responseData.put("totalAmount", order.getTotalAmount());
        responseData.put("createdAt", order.getCreatedAt());

        return ResponseEntity.ok(ApiResponse.success(responseData, "Order received successfully. Tracking ID: " + order.getOrderTrackingId()));
    }

    /**
     * Endpoint to track the status of a specific order.
     */
    @GetMapping("/status/{trackingId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOrderStatus(@PathVariable String trackingId) {
        Optional<Order> orderOpt = orderRepository.findByOrderTrackingId(trackingId);

        if (orderOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Order order = orderOpt.get();
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("orderId", order.getId());
        responseData.put("orderTrackingId", order.getOrderTrackingId());
        responseData.put("status", order.getStatus());
        responseData.put("totalAmount", order.getTotalAmount());
        responseData.put("createdAt", order.getCreatedAt());

        return ResponseEntity.ok(ApiResponse.success(responseData, "Order status retrieved."));
    }

    /**
     * Exposes stats, active threads, and catalog state for the dashboard.
     */
    @GetMapping("/metrics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        // Order statistics
        Map<String, Long> orderStats = new HashMap<>();
        orderStats.put("total", orderRepository.count());
        orderStats.put("pending", orderRepository.countByStatus(OrderStatus.PENDING));
        orderStats.put("success", orderRepository.countByStatus(OrderStatus.SUCCESS));
        orderStats.put("failed", orderRepository.countByStatus(OrderStatus.FAILED));
        metrics.put("orders", orderStats);

        // Thread pool utilization
        Map<String, Object> poolStats = new HashMap<>();
        poolStats.put("activeThreads", orderTaskExecutor.getActiveCount());
        poolStats.put("poolSize", orderTaskExecutor.getPoolSize());
        poolStats.put("corePoolSize", orderTaskExecutor.getCorePoolSize());
        poolStats.put("maxPoolSize", orderTaskExecutor.getMaxPoolSize());
        poolStats.put("queueSize", orderTaskExecutor.getThreadPoolExecutor().getQueue().size());
        poolStats.put("completedTasks", orderTaskExecutor.getThreadPoolExecutor().getCompletedTaskCount());
        metrics.put("threadPool", poolStats);

        // Inventory status (Cache vs DB)
        List<Product> products = productRepository.findAll();
        List<Map<String, Object>> inventoryList = products.stream().map(p -> {
            Map<String, Object> item = new HashMap<>();
            item.put("sku", p.getSku());
            item.put("name", p.getName());
            item.put("price", p.getPrice());
            item.put("dbStock", p.getStockQuantity());
            item.put("version", p.getVersion());
            
            // Get from in-memory cache
            Integer cachedStock = inventoryCacheService.getStock(p.getSku());
            item.put("cacheStock", cachedStock != null ? cachedStock : 0);
            return item;
        }).collect(Collectors.toList());
        metrics.put("inventory", inventoryList);

        return ResponseEntity.ok(ApiResponse.success(metrics, "Metrics fetched successfully."));
    }

    /**
     * Fetches the 10 most recent orders for display.
     */
    @GetMapping("/recent")
    public ResponseEntity<ApiResponse<List<Order>>> getRecentOrders() {
        List<Order> recent = orderRepository.findRecentOrders(PageRequest.of(0, 10));
        return ResponseEntity.ok(ApiResponse.success(recent, "Recent orders fetched successfully."));
    }

    /**
     * Seeds initial database products and rewarms the cache.
     */
    @PostMapping("/admin/seed")
    public ResponseEntity<ApiResponse<String>> seedDatabase() {
        log.info("Seeding products database...");
        
        // Clear existing products if catalog is empty or just re-add
        if (productRepository.count() == 0) {
            List<Product> initialCatalog = List.of(
                Product.builder().sku("SKU-IPHONE").name("Apple iPhone 15 Pro").price(new BigDecimal("999.99")).stockQuantity(100).build(),
                Product.builder().sku("SKU-MACBOOK").name("MacBook Pro 16 M3").price(new BigDecimal("2499.99")).stockQuantity(50).build(),
                Product.builder().sku("SKU-AIRPODS").name("Apple AirPods Pro 2").price(new BigDecimal("249.00")).stockQuantity(200).build(),
                Product.builder().sku("SKU-WATCH").name("Apple Watch Series 9").price(new BigDecimal("399.00")).stockQuantity(150).build(),
                Product.builder().sku("SKU-IPAD").name("Apple iPad Pro 11").price(new BigDecimal("799.00")).stockQuantity(80).build()
            );
            productRepository.saveAll(initialCatalog);
            log.info("Products database seeded.");
        }
        
        inventoryCacheService.initializeCache();
        return ResponseEntity.ok(ApiResponse.success("Database seeded and cache reloaded successfully.", "Success"));
    }

    /**
     * Resets orders and sets products stock back to default seed levels.
     */
    @PostMapping("/admin/reset")
    public ResponseEntity<ApiResponse<String>> resetSimulation() {
        log.info("Resetting simulation state...");
        
        // Delete all orders (cascades to order_items)
        orderRepository.deleteAll();
        
        // Reset stock levels
        List<Product> products = productRepository.findAll();
        for (Product p : products) {
            if ("SKU-IPHONE".equals(p.getSku())) p.setStockQuantity(100);
            else if ("SKU-MACBOOK".equals(p.getSku())) p.setStockQuantity(50);
            else if ("SKU-AIRPODS".equals(p.getSku())) p.setStockQuantity(200);
            else if ("SKU-WATCH".equals(p.getSku())) p.setStockQuantity(150);
            else if ("SKU-IPAD".equals(p.getSku())) p.setStockQuantity(80);
            productRepository.save(p);
        }
        
        inventoryCacheService.initializeCache();
        log.info("Simulation state reset complete.");
        return ResponseEntity.ok(ApiResponse.success("Simulation environment reset successfully.", "Success"));
    }
}
