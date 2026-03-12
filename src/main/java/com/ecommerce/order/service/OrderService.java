// File: src/main/java/com/ecommerce/order/service/OrderService.java
package com.ecommerce.order.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.order.dto.OrderDetailResponse;
import com.ecommerce.order.dto.OrderSummaryResponse;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    /** Get paginated order history for current user */
    public Page<OrderSummaryResponse> getUserOrders(UUID userId, int page, int size) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
                .map(this::toSummary);
    }

    /** Get full order detail - validates user owns the order */
    @Transactional(readOnly = true)
    public OrderDetailResponse getOrderDetail(UUID userId, UUID orderId) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        // Security: ensure user owns this order (or is admin - handled at controller level)
        if (!order.getUser().getId().equals(userId)) {
            throw new BusinessException("ORDER_ACCESS_DENIED", "Order not found", HttpStatus.NOT_FOUND);
        }

        return toDetail(order);
    }

    /** Admin: advance order status */
    @Transactional
    public OrderDetailResponse updateStatus(UUID orderId, Order.OrderStatus newStatus) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        order.setStatus(newStatus);
        orderRepository.save(order);
        return toDetail(order);
    }

    private OrderSummaryResponse toSummary(Order order) {
        return new OrderSummaryResponse(
                order.getId(), order.getStatus().name(),
                order.getTotalAmount(), order.getCreatedAt());
    }

    private OrderDetailResponse toDetail(Order order) {
        var items = order.getItems().stream()
                .map(item -> new OrderDetailResponse.OrderItemResponse(
                        item.getId(), item.getProduct().getId(),
                        item.getProductNameSnapshot(), item.getUnitPrice(),
                        item.getQuantity(), item.getSubtotal()))
                .toList();
        return new OrderDetailResponse(
                order.getId(), order.getStatus().name(),
                order.getShippingAddress(), order.getTotalAmount(),
                items, order.getCreatedAt(), order.getUpdatedAt());
    }
}
