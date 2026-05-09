package com.ecommerce.payment.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * For now: simple card-token based simulation.
 * Later: can evolve into multiple payment types (card, paypal, etc.)
 */
public record PaymentDetails(
        @NotBlank String token
) {}