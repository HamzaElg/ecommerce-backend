// File: src/main/java/com/ecommerce/payment/repository/PaymentRepository.java
package com.ecommerce.payment.repository;

import com.ecommerce.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
    List<Payment> findByOrderId(UUID orderId);
    Optional<Payment> findByOrderIdAndStatus(UUID orderId, Payment.PaymentStatus status);
}
