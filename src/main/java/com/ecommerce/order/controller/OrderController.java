// File: src/main/java/com/ecommerce/order/controller/OrderController.java
package com.ecommerce.order.controller;

import com.ecommerce.common.dto.ApiResponse;
import com.ecommerce.common.dto.PaginationInfo;
import com.ecommerce.order.dto.OrderDetailResponse;
import com.ecommerce.order.dto.OrderSummaryResponse;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Orders")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/api/v1/orders")
    @Operation(summary = "Get order history for current user")
    public ResponseEntity<ApiResponse<java.util.List<OrderSummaryResponse>>> getOrders(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<OrderSummaryResponse> orders = orderService.getUserOrders(userId, page, size);
        return ResponseEntity.ok(ApiResponse.success(orders.getContent(), PaginationInfo.from(orders)));
    }

    @GetMapping("/api/v1/orders/{id}")
    @Operation(summary = "Get order detail")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> getOrder(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrderDetail(userId, id)));
    }

    @PatchMapping("/api/v1/admin/orders/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update order status (Admin only)")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> updateStatus(
            @PathVariable UUID id,
            @RequestParam Order.OrderStatus status) {
        return ResponseEntity.ok(ApiResponse.success(orderService.updateStatus(id, status)));
    }
}
