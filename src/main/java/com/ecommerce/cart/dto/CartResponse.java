// File: src/main/java/com/ecommerce/cart/dto/CartResponse.java
package com.ecommerce.cart.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CartResponse(
    UUID cartId,
    List<CartItemResponse> items,
    BigDecimal totalAmount
) {
    public record CartItemResponse(
        UUID itemId,
        UUID productId,
        String productName,
        String brand,
        String imageUrl,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
    ) {}
}
