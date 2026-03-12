// File: src/main/java/com/ecommerce/analytics/dto/TopProductDto.java
package com.ecommerce.analytics.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TopProductDto(UUID productId, String productName, long totalSold, BigDecimal revenue) {}
