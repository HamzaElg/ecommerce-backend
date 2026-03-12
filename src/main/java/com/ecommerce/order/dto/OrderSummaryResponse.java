// File: src/main/java/com/ecommerce/order/dto/OrderSummaryResponse.java
package com.ecommerce.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderSummaryResponse(UUID orderId, String status, BigDecimal totalAmount, Instant createdAt) {}
