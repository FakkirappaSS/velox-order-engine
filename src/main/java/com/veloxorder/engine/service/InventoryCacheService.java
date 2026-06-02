package com.veloxorder.engine.service;

import com.veloxorder.engine.entity.Product;
import com.veloxorder.engine.exception.ProductNotFoundException;
import com.veloxorder.engine.repository.ProductRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryCacheService {

    private final ProductRepository productRepository;
    private final ConcurrentHashMap<String, Integer> cache = new ConcurrentHashMap<>();

    @PostConstruct
    public void initializeCache() {
        log.info("Initializing inventory cache from database...");
        cache.clear();
        productRepository.findAll().forEach(product -> {
            cache.put(product.getSku(), product.getStockQuantity());
            log.info("Cached SKU: {}, Stock: {}", product.getSku(), product.getStockQuantity());
        });
        log.info("Inventory cache initialization complete. Cached {} products.", cache.size());
    }

    /**
     * Peeks at the stock level in the cache without decrementing it.
     */
    public Integer getStock(String sku) {
        return cache.get(sku);
    }

    /**
     * Atomically decrements the stock for a given SKU in-memory if sufficient stock is available.
     * Returns true if decrement succeeded, false if stock is insufficient.
     * Throws ProductNotFoundException if the SKU does not exist in the cache.
     */
    public boolean decrementStock(String sku, int quantity) {
        if (!cache.containsKey(sku)) {
            throw new ProductNotFoundException("Product with SKU '" + sku + "' not found in cache");
        }

        AtomicBoolean success = new AtomicBoolean(false);
        cache.computeIfPresent(sku, (k, currentStock) -> {
            if (currentStock >= quantity) {
                success.set(true);
                return currentStock - quantity;
            }
            return currentStock;
        });

        return success.get();
    }

    /**
     * Updates or inserts stock level directly in the cache.
     */
    public void putStock(String sku, int quantity) {
        cache.put(sku, quantity);
    }

    /**
     * Returns an unmodifiable view of the current cache.
     */
    public Map<String, Integer> getCacheSnapshot() {
        return Collections.unmodifiableMap(cache);
    }
}
