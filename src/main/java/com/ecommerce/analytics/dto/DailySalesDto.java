// File: src/main/java/com/ecommerce/analytics/dto/DailySalesDto.java
package com.ecommerce.analytics.dto;

import java.math.BigDecimal;

public record DailySalesDto(String date, long orderCount, BigDecimal revenue) {}
