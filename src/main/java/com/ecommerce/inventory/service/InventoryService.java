// File: src/main/java/com/ecommerce/inventory/service/InventoryService.java
package com.ecommerce.inventory.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.inventory.dto.InventoryResponse;
import com.ecommerce.inventory.dto.UpdateStockRequest;
import com.ecommerce.inventory.entity.Inventory;
import com.ecommerce.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    @Transactional(readOnly = true)
    public List<InventoryResponse> getAllInventory() {
        return inventoryRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public InventoryResponse getInventory(UUID productId) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", productId));

        return toResponse(inventory);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "product-detail", key = "#productId"),
            @CacheEvict(value = "product-search", allEntries = true)
    })
    public InventoryResponse updateStock(UUID productId, UpdateStockRequest request) {
        Inventory inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", productId));

        if (request.stockQty() < 0) {
            throw new BusinessException(
                    "INVALID_STOCK_QUANTITY",
                    "Stock quantity cannot be negative",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (request.stockQty() < inventory.getReservedQty()) {
            throw new BusinessException(
                    "INVALID_STOCK_QUANTITY",
                    "Stock quantity cannot be lower than reserved quantity",
                    HttpStatus.CONFLICT
            );
        }

        inventory.setStockQty(request.stockQty());

        inventory = inventoryRepository.save(inventory);
        return toResponse(inventory);
    }

    private InventoryResponse toResponse(Inventory inventory) {
        return new InventoryResponse(
                inventory.getProductId(),
                inventory.getProduct().getName(),
                inventory.getProduct().getBrand(),
                inventory.getStockQty(),
                inventory.getReservedQty(),
                inventory.getAvailableQty(),
                inventory.getVersion(),
                inventory.getUpdatedAt()
        );
    }
}