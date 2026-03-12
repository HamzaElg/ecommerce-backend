// File: src/main/java/com/ecommerce/checkout/dto/CheckoutResponse.java
package com.ecommerce.checkout.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CheckoutResponse(UUID orderId, String status, BigDecimal totalAmount) {}
