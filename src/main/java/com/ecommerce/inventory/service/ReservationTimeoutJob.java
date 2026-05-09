// File: src/main/java/com/ecommerce/inventory/service/ReservationTimeoutJob.java
package com.ecommerce.inventory.service;

import com.ecommerce.inventory.repository.InventoryRepository;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationTimeoutJob {

    private final OrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;

    @Value("${app.reservation.timeout-minutes:30}")
    private int timeoutMinutes;

    @Scheduled(fixedDelay = 300_000)
    @Transactional
    public void releaseExpiredReservations() {
        Instant cutoff = Instant.now().minus(timeoutMinutes, ChronoUnit.MINUTES);

        List<Order> expiredOrders = orderRepository.findExpiredPendingOrders(cutoff);

        if (expiredOrders.isEmpty()) {
            return;
        }

        log.info("Found {} expired pending orders to cancel", expiredOrders.size());

        for (Order order : expiredOrders) {
            try {
                cancelExpiredOrder(order);
            } catch (Exception e) {
                log.error("Failed to cancel expired order: {}", order.getId(), e);
            }
        }
    }

    private void cancelExpiredOrder(Order order) {
        if (order.getStatus() != Order.OrderStatus.PENDING_PAYMENT) {
            log.info("Skipping order {} because status is {}", order.getId(), order.getStatus());
            return;
        }

        releaseOrderReservations(order);

        order.setStatus(Order.OrderStatus.CANCELLED);
        orderRepository.save(order);

        log.info("Cancelled expired order: {}", order.getId());
    }

    private void releaseOrderReservations(Order order) {
        for (OrderItem item : order.getItems()) {
            inventoryRepository.findByProductIdWithLock(item.getProduct().getId())
                    .ifPresent(inventory -> {
                        inventory.releaseReservation(item.getQuantity());
                        inventoryRepository.save(inventory);

                        log.info(
                                "Released reservation: orderId={}, productId={}, quantity={}",
                                order.getId(),
                                item.getProduct().getId(),
                                item.getQuantity()
                        );
                    });
        }
    }
}