package com.ecommerce.payment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PaymentRequest(
        @NotNull UUID orderId,

        @NotBlank String method,

        @NotNull @Valid PaymentDetails paymentDetails
) {}