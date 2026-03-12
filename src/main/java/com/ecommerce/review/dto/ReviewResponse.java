// File: src/main/java/com/ecommerce/review/dto/ReviewResponse.java
package com.ecommerce.review.dto;

import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(UUID reviewId, UUID userId, UUID productId, int rating, String comment, Instant createdAt) {}
