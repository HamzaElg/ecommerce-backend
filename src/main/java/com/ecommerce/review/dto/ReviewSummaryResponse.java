// File: src/main/java/com/ecommerce/review/dto/ReviewSummaryResponse.java
package com.ecommerce.review.dto;

import java.time.Instant;
import java.util.UUID;

public record ReviewSummaryResponse(
    UUID reviewId, String reviewerName,
    int rating, String comment, Instant createdAt,
    double averageRating, int totalReviews
) {}
