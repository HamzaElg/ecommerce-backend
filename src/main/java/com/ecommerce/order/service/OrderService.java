// File: src/main/java/com/ecommerce/order/service/OrderService.java
package com.ecommerce.order.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.inventory.entity.Inventory;
import com.ecommerce.inventory.repository.InventoryRepository;
import com.ecommerce.order.dto.OrderDetailResponse;
import com.ecommerce.order.dto.OrderSummaryResponse;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.product.service.ProductCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final int MAX_PAGE_SIZE = 100;

    private final OrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductCacheService productCacheService;

    /** Customer: get paginated order history for current user only */
    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> getUserOrders(UUID userId, int page, int size) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(
                        userId,
                        PageRequest.of(safePage(page), safeSize(size))
                )
                .map(this::toSummary);
    }

    /** Customer: get detail only if the order belongs to current user */
    @Transactional(readOnly = true)
    public OrderDetailResponse getOrderDetail(UUID userId, UUID orderId) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (!order.getUser().getId().equals(userId)) {
            throw new BusinessException(
                    "ORDER_ACCESS_DENIED",
                    "Order not found",
                    HttpStatus.NOT_FOUND
            );
        }

        return toDetail(order);
    }

    /** Admin: list all orders, optionally filtered by status */
    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> getAllOrdersForAdmin(
            Order.OrderStatus status,
            int page,
            int size
    ) {
        PageRequest pageable = PageRequest.of(safePage(page), safeSize(size));

        Page<Order> orders = status == null
                ? orderRepository.findAllByOrderByCreatedAtDesc(pageable)
                : orderRepository.findByStatusOrderByCreatedAtDesc(status, pageable);

        return orders.map(this::toSummary);
    }

    /** Admin: view any order */
    @Transactional(readOnly = true)
    public OrderDetailResponse getOrderDetailForAdmin(UUID orderId) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        return toDetail(order);
    }

    /** Admin: update order status with minimum sane transition rules */
    @Transactional
    public OrderDetailResponse updateOrderStatusAsAdmin(UUID orderId, Order.OrderStatus newStatus) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        Order.OrderStatus currentStatus = order.getStatus();

        if (currentStatus == newStatus) {
            return toDetail(order);
        }

        validateStatusTransition(currentStatus, newStatus);

        if (currentStatus == Order.OrderStatus.PENDING_PAYMENT
                && newStatus == Order.OrderStatus.CANCELLED) {
            releasePendingReservations(order);
        }

        order.setStatus(newStatus);
        order = orderRepository.save(order);

        return toDetail(order);
    }

    private void validateStatusTransition(Order.OrderStatus current, Order.OrderStatus next) {
        boolean allowed =
                (current == Order.OrderStatus.PENDING_PAYMENT && next == Order.OrderStatus.CANCELLED)
                        || (current == Order.OrderStatus.PAID && next == Order.OrderStatus.SHIPPED)
                        || (current == Order.OrderStatus.PAID && next == Order.OrderStatus.REFUNDED)
                        || (current == Order.OrderStatus.SHIPPED && next == Order.OrderStatus.DELIVERED);

        if (!allowed) {
            throw new BusinessException(
                    "INVALID_ORDER_STATUS_TRANSITION",
                    "Cannot change order status from " + current + " to " + next,
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
    }

    private void releasePendingReservations(Order order) {
        for (OrderItem item : order.getItems()) {
            Inventory inventory = inventoryRepository.findByProductIdWithLock(item.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory", item.getProduct().getId()));

            inventory.releaseReservation(item.getQuantity());
            inventoryRepository.save(inventory);

            productCacheService.evictProduct(item.getProduct().getId());
        }
    }

    private OrderSummaryResponse toSummary(Order order) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getCreatedAt()
        );
    }

    private OrderDetailResponse toDetail(Order order) {
        var items = order.getItems().stream()
                .map(item -> new OrderDetailResponse.OrderItemResponse(
                        item.getId(),
                        item.getProduct().getId(),
                        item.getProductNameSnapshot(),
                        item.getUnitPrice(),
                        item.getQuantity(),
                        item.getSubtotal()
                ))
                .toList();

        return new OrderDetailResponse(
                order.getId(),
                order.getStatus().name(),
                order.getShippingAddress(),
                order.getTotalAmount(),
                items,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private int safePage(int page) {
        return Math.max(page, 0);
    }

    private int safeSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}