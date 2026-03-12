// File: src/test/java/com/ecommerce/payment/PaymentServiceTest.java
package com.ecommerce.payment;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.inventory.entity.Inventory;
import com.ecommerce.inventory.repository.InventoryRepository;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.payment.dto.PaymentRequest;
import com.ecommerce.payment.dto.PaymentResponse;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.repository.PaymentRepository;
import com.ecommerce.payment.service.PaymentService;
import com.ecommerce.product.entity.Product;
import com.ecommerce.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock OrderRepository orderRepository;
    @Mock InventoryRepository inventoryRepository;
    @InjectMocks PaymentService paymentService;

    private Order buildPendingOrder(UUID userId, UUID productId, int qty) {
        User user = User.builder().id(userId).build();
        Product product = Product.builder().id(productId).build();
        OrderItem item = OrderItem.builder().product(product).quantity(qty)
                .unitPrice(new BigDecimal("100")).productNameSnapshot("Test Product").build();

        return Order.builder()
                .id(UUID.randomUUID())
                .user(user)
                .status(Order.OrderStatus.PENDING_PAYMENT)
                .totalAmount(new BigDecimal("100"))
                .items(new ArrayList<>(List.of(item)))
                .idempotencyKey("key-1")
                .build();
    }

    @Test
    void payment_success_updatesOrderAndStock() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Order order = buildPendingOrder(userId, productId, 1);

        Inventory inventory = Inventory.builder()
                .productId(productId).stockQty(10).reservedQty(1).version(0L).build();
        Payment savedPayment = Payment.builder()
                .id(UUID.randomUUID()).status(Payment.PaymentStatus.SUCCESS)
                .amount(order.getTotalAmount()).idempotencyKey("new-key")
                .providerReference("SIM-123").build();

        when(paymentRepository.findByIdempotencyKey("new-key")).thenReturn(Optional.empty());
        when(orderRepository.findByIdWithItems(order.getId())).thenReturn(Optional.of(order));
        when(inventoryRepository.findByProductIdWithLock(productId)).thenReturn(Optional.of(inventory));
        when(paymentRepository.save(any())).thenReturn(savedPayment);
        when(orderRepository.save(any())).thenReturn(order);

        PaymentResponse response = paymentService.processPayment(userId, "new-key",
                new PaymentRequest(order.getId(), "CREDIT_CARD", null));

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.PAID);
        // Stock confirmed: stockQty 10-1=9, reservedQty 1-1=0
        assertThat(inventory.getStockQty()).isEqualTo(9);
        assertThat(inventory.getReservedQty()).isEqualTo(0);
    }

    @Test
    void payment_failure_releasesReservation() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Order order = buildPendingOrder(userId, productId, 2);

        Inventory inventory = Inventory.builder()
                .productId(productId).stockQty(10).reservedQty(2).version(0L).build();
        Payment failedPayment = Payment.builder()
                .id(UUID.randomUUID()).status(Payment.PaymentStatus.FAILED)
                .amount(order.getTotalAmount()).idempotencyKey("fail-key")
                .providerReference("SIM-fail").build();

        when(paymentRepository.findByIdempotencyKey("fail-key")).thenReturn(Optional.empty());
        when(orderRepository.findByIdWithItems(order.getId())).thenReturn(Optional.of(order));
        when(inventoryRepository.findByProductIdWithLock(productId)).thenReturn(Optional.of(inventory));
        when(paymentRepository.save(any())).thenReturn(failedPayment);
        when(orderRepository.save(any())).thenReturn(order);

        PaymentResponse response = paymentService.processPayment(userId, "fail-key",
                new PaymentRequest(order.getId(), "FAIL_TEST", null));

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.FAILED);
        assertThat(inventory.getReservedQty()).isEqualTo(0); // Reservation released
        assertThat(inventory.getStockQty()).isEqualTo(10);  // Stock unchanged
    }

    @Test
    void payment_idempotency_returnsSameResult() {
        UUID userId = UUID.randomUUID();
        Payment existing = Payment.builder()
                .id(UUID.randomUUID()).status(Payment.PaymentStatus.SUCCESS)
                .amount(new BigDecimal("100")).idempotencyKey("dup-key")
                .providerReference("SIM-exists").build();

        when(paymentRepository.findByIdempotencyKey("dup-key")).thenReturn(Optional.of(existing));

        var response = paymentService.processPayment(userId, "dup-key",
                new PaymentRequest(UUID.randomUUID(), "CARD", null));

        assertThat(response.status()).isEqualTo("SUCCESS");
        verify(orderRepository, never()).findByIdWithItems(any());  // No reprocessing
    }

    @Test
    void payment_wrongUser_throwsAccessDenied() {
        UUID userId = UUID.randomUUID();
        UUID differentUser = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Order order = buildPendingOrder(differentUser, productId, 1);

        when(paymentRepository.findByIdempotencyKey("key")).thenReturn(Optional.empty());
        when(orderRepository.findByIdWithItems(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.processPayment(userId, "key",
                new PaymentRequest(order.getId(), "CARD", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not belong");
    }
}
