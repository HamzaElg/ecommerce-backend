package com.ecommerce.inventory.dto;

import java.time.Instant;
import java.util.UUID;

public record InventoryResponse(
        UUID productId,
        String productName,
        String brand,
        int stockQty,
        int reservedQty,
        int availableQty,
        Long version,
        Instant updatedAt
) {}