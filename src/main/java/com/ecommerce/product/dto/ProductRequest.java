// File: src/main/java/com/ecommerce/product/dto/ProductRequest.java
package com.ecommerce.product.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProductRequest(
    @NotBlank @Size(max = 255) String name,
    @NotBlank @Size(max = 100) String brand,
    String description,
    @NotNull @DecimalMin("0.00") BigDecimal price,
    @NotNull UUID categoryId,
    Map<String, Object> specs,
    List<String> imageUrls
) {}
