package com.veloxorder.engine.service;

import com.veloxorder.engine.entity.Product;
import com.veloxorder.engine.exception.InsufficientStockException;
import com.veloxorder.engine.exception.ProductNotFoundException;
import com.veloxorder.engine.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhysicalStockDeductionService {

    private final ProductRepository productRepository;

    /**
     * Deducts stock directly from the database for a specific SKU.
     * Uses REQUIRES_NEW propagation to ensure a fresh database transaction is opened
     * for every retry attempt, bypassing Hibernate's internal level 1 cache.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deductStock(String sku, int quantity) {
        log.info("[{}] DB physical stock decrement attempt for SKU: {}, quantity: {}",
                Thread.currentThread().getName(), sku, quantity);

        Product product = productRepository.findBySku(sku)
                .orElseThrow(() -> new ProductNotFoundException("Product with SKU '" + sku + "' not found in database"));

        if (product.getStockQuantity() < quantity) {
            throw new InsufficientStockException("Insufficient physical database stock for SKU '" + sku +
                    "'. Required: " + quantity + ", Available: " + product.getStockQuantity());
        }

        product.setStockQuantity(product.getStockQuantity() - quantity);
        productRepository.saveAndFlush(product);

        log.info("[{}] DB physical stock successfully decremented for SKU: {}. New Stock: {}, Version: {}",
                Thread.currentThread().getName(), sku, product.getStockQuantity(), product.getVersion());
    }
}
