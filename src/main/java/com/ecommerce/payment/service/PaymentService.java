// File: src/main/java/com/ecommerce/payment/service/PaymentService.java
package com.ecommerce.payment.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.inventory.entity.Inventory;
import com.ecommerce.inventory.repository.InventoryRepository;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.payment.dto.PaymentRequest;
import com.ecommerce.payment.dto.PaymentResponse;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Payment service - processes payments with idempotency.
 *
 * Payment flow:
 * 1. Validate idempotency key (return existing payment if already processed)
 * 2. Validate order belongs to user and is in PENDING_PAYMENT status
 * 3. Simulate payment provider call (stub for real integration)
 * 4. On SUCCESS:
 *    - stockQty -= quantity (consumed from stock)
 *    - reservedQty -= quantity (reservation fulfilled)
 *    - order status -> PAID
 * 5. On FAILURE:
 *    - reservedQty -= quantity (release reservation)
 *    - order status -> FAILED
 *    - Design choice: release reservation on failure so stock becomes available again immediately
 *    - Alternative: keep reservation for retry window (more complex, not needed for MVP)
 *
 * Idempotency: if same idempotency key is used again, return existing payment.
 * This handles network retries safely.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;

    @Transactional
    public PaymentResponse processPayment(UUID userId, String idempotencyKey, PaymentRequest request) {

        // IDEMPOTENCY: return existing payment if already processed
        return paymentRepository.findByIdempotencyKey(idempotencyKey)
                .map(existing -> {
                    log.info("Duplicate payment request. Returning existing payment: {}", existing.getId());
                    return toResponse(existing);
                })
                .orElseGet(() -> executePayment(userId, idempotencyKey, request));
    }

    private PaymentResponse executePayment(UUID userId, String idempotencyKey, PaymentRequest request) {
        Order order = orderRepository.findByIdWithItems(request.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", request.orderId()));

        // Security: ensure user owns this order
        if (!order.getUser().getId().equals(userId)) {
            throw new BusinessException("ORDER_ACCESS_DENIED", "Order does not belong to this user", HttpStatus.FORBIDDEN);
        }

        // Business rule: can only pay for orders in PENDING_PAYMENT status
        if (order.getStatus() != Order.OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException("ORDER_NOT_PAYABLE",
                    "Order cannot be paid in status: " + order.getStatus(), HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // Simulate payment provider integration
        // In production: call Stripe, PayPal, etc. here and handle their async webhooks
        boolean paymentSucceeded = simulatePaymentProvider(request);

        Payment payment = Payment.builder()
                .order(order)
                .method(request.method())
                .idempotencyKey(idempotencyKey)
                .amount(order.getTotalAmount())
                .providerReference("SIM-" + UUID.randomUUID())  // Simulated provider ref
                .build();

        if (paymentSucceeded) {
            payment.setStatus(Payment.PaymentStatus.SUCCESS);
            order.setStatus(Order.OrderStatus.PAID);

            // STOCK CONFIRMATION: deduct from actual stock (reservation fulfilled)
            for (OrderItem item : order.getItems()) {
                Inventory inventory = inventoryRepository.findByProductIdWithLock(item.getProduct().getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Inventory", item.getProduct().getId()));
                inventory.confirmSale(item.getQuantity());
                inventoryRepository.save(inventory);
            }

            log.info("Payment SUCCESS: orderId={}, amount={}", order.getId(), order.getTotalAmount());
        } else {
            payment.setStatus(Payment.PaymentStatus.FAILED);
            order.setStatus(Order.OrderStatus.FAILED);

            // RELEASE RESERVATION: make stock available for other customers
            for (OrderItem item : order.getItems()) {
                Inventory inventory = inventoryRepository.findByProductIdWithLock(item.getProduct().getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Inventory", item.getProduct().getId()));
                inventory.releaseReservation(item.getQuantity());
                inventoryRepository.save(inventory);
            }

            log.warn("Payment FAILED: orderId={}", order.getId());
        }

        orderRepository.save(order);
        payment = paymentRepository.save(payment);
        return toResponse(payment);
    }

    /**
     * Simulated payment provider.
     * In production: integrate with Stripe, PayPal, or another PSP here.
     * The interface is clean enough that replacing this with a real provider call
     * only requires changing this method.
     *
     * Design: for simulation, payments with method "FAIL_TEST" always fail.
     */
    private boolean simulatePaymentProvider(PaymentRequest request) {
        if ("FAIL_TEST".equals(request.method())) {
            return false;
        }
        // Simulate ~95% success rate in production testing
        return true;
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getStatus().name(), payment.getAmount(), payment.getProviderReference());
    }
}
