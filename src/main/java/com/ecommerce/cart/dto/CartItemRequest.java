// File: src/main/java/com/ecommerce/cart/dto/CartItemRequest.java
package com.ecommerce.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CartItemRequest(
    @NotNull UUID productId,
    @Min(1) int quantity
) {}
