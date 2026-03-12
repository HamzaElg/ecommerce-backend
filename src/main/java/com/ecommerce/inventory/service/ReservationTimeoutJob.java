// File: src/main/java/com/ecommerce/inventory/service/ReservationTimeoutJob.java
package com.ecommerce.inventory.service;

import com.ecommerce.inventory.repository.InventoryRepository;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled job to release expired stock reservations.
 *
 * Business rule: if an order stays in PENDING_PAYMENT for more than
 * 30 minutes (configurable), it is considered abandoned.
 * We release the reserved stock so it becomes available for other customers.
 *
 * This prevents stock being permanently reserved by users who started
 * checkout but never completed payment.
 *
 * Runs every 5 minutes. In production, this could be replaced with
 * a message queue approach (e.g. Kafka delayed messages) for more precise timing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationTimeoutJob {

    private final OrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;

    @Value("${app.reservation.timeout-minutes:30}")
    private int timeoutMinutes;

    @Scheduled(fixedDelay = 300_000)  // Every 5 minutes
    @Transactional
    public void releaseExpiredReservations() {
        Instant cutoff = Instant.now().minus(timeoutMinutes, ChronoUnit.MINUTES);

        // Find orders stuck in PENDING_PAYMENT beyond the timeout window
        // Using a native query approach for efficiency
        List<Order> expiredOrders = orderRepository.findExpiredPendingOrders(cutoff);

        if (expiredOrders.isEmpty()) return;

        log.info("Releasing reservations for {} expired orders", expiredOrders.size());

        for (Order order : expiredOrders) {
            try {
                releaseOrderReservations(order);
                order.setStatus(Order.OrderStatus.CANCELLED);
                orderRepository.save(order);
                log.info("Cancelled expired order: {}", order.getId());
            } catch (Exception e) {
                log.error("Failed to release reservation for order: {}", order.getId(), e);
            }
        }
    }

    private void releaseOrderReservations(Order order) {
        for (OrderItem item : order.getItems()) {
            inventoryRepository.findByProductIdWithLock(item.getProduct().getId())
                    .ifPresent(inventory -> {
                        inventory.releaseReservation(item.getQuantity());
                        inventoryRepository.save(inventory);
                    });
        }
    }
}
