// File: src/main/java/com/ecommerce/product/service/ProductCacheService.java
package com.ecommerce.product.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductCacheService {

    private static final String PRODUCT_SEARCH_CACHE = "product-search";
    private static final String PRODUCT_DETAIL_CACHE = "product-detail";

    private final CacheManager cacheManager;

    public void evictProduct(UUID productId) {
        runAfterCommit(() -> {
            evictProductDetail(productId);
            evictProductSearch();
        });
    }

    public void evictProductSearchOnly() {
        runAfterCommit(this::evictProductSearch);
    }

    public void evictAllProductCaches() {
        runAfterCommit(() -> {
            evictProductSearch();
            evictAllProductDetails();
        });
    }

    private void evictProductDetail(UUID productId) {
        Cache cache = cacheManager.getCache(PRODUCT_DETAIL_CACHE);
        if (cache != null) {
            cache.evict(productId);
            log.debug("Evicted product-detail cache for productId={}", productId);
        }
    }

    private void evictProductSearch() {
        Cache cache = cacheManager.getCache(PRODUCT_SEARCH_CACHE);
        if (cache != null) {
            cache.clear();
            log.debug("Cleared product-search cache");
        }
    }

    private void evictAllProductDetails() {
        Cache cache = cacheManager.getCache(PRODUCT_DETAIL_CACHE);
        if (cache != null) {
            cache.clear();
            log.debug("Cleared product-detail cache");
        }
    }

    private void runAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}