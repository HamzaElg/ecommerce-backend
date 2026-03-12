// File: src/main/java/com/ecommerce/analytics/controller/AnalyticsController.java
package com.ecommerce.analytics.controller;

import com.ecommerce.analytics.dto.DailySalesDto;
import com.ecommerce.analytics.dto.TopProductDto;
import com.ecommerce.analytics.repository.AnalyticsRepository;
import com.ecommerce.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Analytics", description = "Admin analytics endpoints")
@SecurityRequirement(name = "bearerAuth")
public class AnalyticsController {

    private final AnalyticsRepository analyticsRepository;

    @GetMapping("/sales/daily")
    @Operation(summary = "Daily sales for last 30 days (Admin only)")
    public ResponseEntity<ApiResponse<List<DailySalesDto>>> dailySales() {
        Instant from = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant to = Instant.now();
        List<Object[]> raw = analyticsRepository.findDailySalesRaw(from, to);
        List<DailySalesDto> result = raw.stream()
                .map(row -> new DailySalesDto(
                        row[0].toString(),
                        ((Number) row[1]).longValue(),
                        new BigDecimal(row[2].toString())))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/sales/weekly")
    @Operation(summary = "Weekly sales for last 12 weeks (Admin only)")
    public ResponseEntity<ApiResponse<List<DailySalesDto>>> weeklySales() {
        Instant from = Instant.now().minus(84, ChronoUnit.DAYS); // 12 weeks
        Instant to = Instant.now();
        List<Object[]> raw = analyticsRepository.findDailySalesRaw(from, to);
        List<DailySalesDto> result = raw.stream()
                .map(row -> new DailySalesDto(row[0].toString(),
                        ((Number) row[1]).longValue(), new BigDecimal(row[2].toString())))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/products/top")
    @Operation(summary = "Top selling products (Admin only)")
    public ResponseEntity<ApiResponse<List<TopProductDto>>> topProducts(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "30") int days) {
        Instant from = Instant.now().minus(days, ChronoUnit.DAYS);
        List<Object[]> raw = analyticsRepository.findTopProductsRaw(from, limit);
        List<TopProductDto> result = raw.stream()
                .map(row -> new TopProductDto(
                        UUID.fromString(row[0].toString()),
                        row[1].toString(),
                        ((Number) row[2]).longValue(),
                        new BigDecimal(row[3].toString())))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/traffic")
    @Operation(summary = "Traffic analytics (Admin only) - placeholder")
    public ResponseEntity<ApiResponse<Object>> traffic() {
        // Placeholder: integrate with your analytics platform (GA, Mixpanel, etc.)
        return ResponseEntity.ok(ApiResponse.success(
                java.util.Map.of("message", "Traffic analytics - integrate with external analytics platform")));
    }
}
