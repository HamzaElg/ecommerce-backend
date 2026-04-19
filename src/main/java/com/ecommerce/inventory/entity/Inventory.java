// File: src/main/java/com/ecommerce/inventory/entity/Inventory.java
package com.ecommerce.inventory.entity;

import com.ecommerce.product.entity.Product;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Inventory for a product.
 *
 * Anti-overselling strategy:
 * - reservedQty: incremented at checkout (before payment)
 * - stockQty: decremented at payment success
 * - availableQty = stockQty - reservedQty (computed, never stored)
 *
 * @Version (optimistic locking): prevents two concurrent checkouts
 * from both succeeding when only 1 unit remains.
 * If two transactions read version=5 and both try to update,
 * one will fail with OptimisticLockingFailureException.
 * The application retries or surfaces INSUFFICIENT_STOCK error.
 */
@Entity
@Table(name = "inventory")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {

    @Id
    @Column(name = "product_id")
    private UUID productId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "stock_qty", nullable = false)
    private int stockQty;

    @Column(name = "reserved_qty", nullable = false)
    private int reservedQty;

    /** JPA optimistic locking - DB version number incremented on each update */
    @Version
    private Long version;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Computed: units available for new orders */
    public int getAvailableQty() {
        return stockQty - reservedQty;
    }

    /**
     * Reserve stock for a pending order.
     * Call this at checkout, before payment.
     */
    public void reserve(int qty) {
        if (getAvailableQty() < qty) {
            throw new IllegalStateException("Not enough stock to reserve");
        }
        this.reservedQty += qty;
    }

    /**
     * Confirm stock deduction after successful payment.
     * Both decrements happen atomically: we "consumed" the reservation.
     */
    public void confirmSale(int qty) {
        this.stockQty -= qty;
        this.reservedQty -= qty;
    }

    /** Release a reservation (on payment failure or timeout). */
    public void releaseReservation(int qty) {
        this.reservedQty = Math.max(0, this.reservedQty - qty);
    }
}
