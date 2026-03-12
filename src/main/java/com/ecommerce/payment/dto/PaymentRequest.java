// File: src/main/java/com/ecommerce/payment/dto/PaymentRequest.java
package com.ecommerce.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PaymentRequest(
    @NotNull UUID orderId,
    @NotBlank String method,
    Object paymentDetails  // Provider-specific payload (card token, etc.)
) {}
