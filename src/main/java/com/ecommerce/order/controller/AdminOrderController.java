// File: src/main/java/com/ecommerce/order/controller/AdminOrderController.java
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
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Orders")
public class AdminOrderController {

    private final OrderService orderService;

    @GetMapping
    @Operation(summary = "Admin: get all orders")
    public ResponseEntity<ApiResponse<java.util.List<OrderSummaryResponse>>> getAllOrders(
            @RequestParam(required = false) Order.OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<OrderSummaryResponse> orders = orderService.getAllOrdersForAdmin(status, page, size);

        return ResponseEntity.ok(
                ApiResponse.success(orders.getContent(), PaginationInfo.from(orders))
        );
    }

    @GetMapping("/{id}")
    @Operation(summary = "Admin: get any order detail")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> getOrder(
            @PathVariable UUID id) {

        return ResponseEntity.ok(
                ApiResponse.success(orderService.getOrderDetailForAdmin(id))
        );
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Admin: update order status")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> updateStatus(
            @PathVariable UUID id,
            @RequestParam Order.OrderStatus status) {

        return ResponseEntity.ok(
                ApiResponse.success(orderService.updateOrderStatusAsAdmin(id, status))
        );
    }
}