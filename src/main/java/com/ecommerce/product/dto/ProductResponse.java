// File: src/main/java/com/ecommerce/product/dto/ProductResponse.java
package com.ecommerce.product.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProductResponse(
    UUID id,
    String name,
    String brand,
    String description,
    BigDecimal price,
    UUID categoryId,
    String categoryName,
    Map<String, Object> specs,
    List<String> imageUrls,
    boolean active,
    int availableStock,
    Double averageRating,
    int reviewCount,
    Instant createdAt
) {}
