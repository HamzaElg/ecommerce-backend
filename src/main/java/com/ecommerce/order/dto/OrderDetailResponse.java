// File: src/main/java/com/ecommerce/order/dto/OrderDetailResponse.java
package com.ecommerce.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record OrderDetailResponse(
    UUID orderId,
    String status,
    Map<String, Object> shippingAddress,
    BigDecimal totalAmount,
    List<OrderItemResponse> items,
    Instant createdAt,
    Instant updatedAt
) {
    public record OrderItemResponse(
        UUID itemId, UUID productId, String productName,
        BigDecimal unitPrice, int quantity, BigDecimal subtotal
    ) {}
}
