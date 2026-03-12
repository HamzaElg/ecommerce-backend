// File: src/main/java/com/ecommerce/checkout/dto/CheckoutRequest.java
package com.ecommerce.checkout.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record CheckoutRequest(
    @NotNull Map<String, Object> shippingAddress
) {}
