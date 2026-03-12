// File: src/main/java/com/ecommerce/inventory/repository/InventoryRepository.java
package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    /**
     * Fetch inventory with PESSIMISTIC_WRITE lock.
     * Used during checkout to prevent race conditions on stock reservation.
     * This acquires a SELECT ... FOR UPDATE lock at DB level.
     *
     * Note: Combined with @Version (optimistic locking) in the entity,
     * we have a dual-layer protection. Pessimistic lock for the critical
     * checkout path, optimistic lock as a safety net.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.productId = :productId")
    Optional<Inventory> findByProductIdWithLock(UUID productId);

    Optional<Inventory> findByProductId(UUID productId);
}
