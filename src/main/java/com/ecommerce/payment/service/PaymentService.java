package com.ecommerce.payment.service;

import com.ecommerce.product.service.ProductCacheService;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductCacheService productCacheService;

    @Transactional
    public PaymentResponse processPayment(UUID userId, String idempotencyKey, PaymentRequest request) {

        // 1. Idempotency check
        return paymentRepository.findByIdempotencyKey(idempotencyKey)
                .map(existing -> {
                    log.info("Duplicate payment request, returning existing: {}", existing.getId());
                    return toResponse(existing);
                })
                .orElseGet(() -> executePayment(userId, idempotencyKey, request));
    }

    private PaymentResponse executePayment(UUID userId, String idempotencyKey, PaymentRequest request) {

        // 2. Fetch order with items
        Order order = orderRepository.findByIdWithItems(request.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", request.orderId()));

        // 3. Ownership check
        if (!order.getUser().getId().equals(userId)) {
            throw new BusinessException(
                    "ORDER_ACCESS_DENIED",
                    "Order does not belong to this user",
                    HttpStatus.FORBIDDEN
            );
        }

        // 4. Status check
        if (order.getStatus() != Order.OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(
                    "ORDER_NOT_PAYABLE",
                    "Order cannot be paid in status: " + order.getStatus(),
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }

        // 5. Simulate provider
        boolean success = simulatePaymentProvider(request);

        Payment payment = Payment.builder()
                .order(order)
                .method(request.method())
                .amount(order.getTotalAmount())
                .idempotencyKey(idempotencyKey)
                .providerReference("SIM-" + UUID.randomUUID())
                .build();

        if (success) {
            handleSuccess(order, payment);
        } else {
            handleFailure(order, payment);
        }

        // 6. Persist
        orderRepository.save(order);
        payment = paymentRepository.save(payment);

        return toResponse(payment);
    }

    // ======================
    // SUCCESS FLOW
    // ======================
    private void handleSuccess(Order order, Payment payment) {
        payment.setStatus(Payment.PaymentStatus.SUCCESS);
        order.setStatus(Order.OrderStatus.PAID);

        for (OrderItem item : order.getItems()) {
            Inventory inventory = inventoryRepository.findByProductIdWithLock(item.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory", item.getProduct().getId()));

            inventory.confirmSale(item.getQuantity());
            inventoryRepository.save(inventory);

            // Clear cached product search/detail because stockQty/reservedQty changed
            productCacheService.evictProduct(item.getProduct().getId());
        }

        log.info("Payment SUCCESS → orderId={}", order.getId());
    }

    // ======================
    // FAILURE FLOW
    // ======================
    private void handleFailure(Order order, Payment payment) {
        payment.setStatus(Payment.PaymentStatus.FAILED);

        // Use your existing enum value (you likely have FAILED)
        order.setStatus(Order.OrderStatus.FAILED);

        for (OrderItem item : order.getItems()) {
            Inventory inventory = inventoryRepository.findByProductIdWithLock(item.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory", item.getProduct().getId()));

            inventory.releaseReservation(item.getQuantity());
            inventoryRepository.save(inventory);

            // Clear cached product search/detail because stockQty/reservedQty changed
            productCacheService.evictProduct(item.getProduct().getId());
        }

        log.warn("Payment FAILED → orderId={}", order.getId());
    }

    // ======================
    // PAYMENT SIMULATION
    // ======================
    private boolean simulatePaymentProvider(PaymentRequest request) {

        String token = request.paymentDetails().token();

        if ("tok_test_fail".equals(token)) {
            return false;
        }

        if ("tok_test_success".equals(token)) {
            return true;
        }

        // Default behavior
        return true;
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getStatus().name(),
                payment.getAmount(),
                payment.getProviderReference()
        );
    }
}