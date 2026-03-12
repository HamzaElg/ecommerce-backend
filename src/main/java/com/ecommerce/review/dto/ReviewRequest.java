// File: src/main/java/com/ecommerce/review/dto/ReviewRequest.java
package com.ecommerce.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReviewRequest(
    @NotNull @Min(1) @Max(5) int rating,
    String comment
) {}
