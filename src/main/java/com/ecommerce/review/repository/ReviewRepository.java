package com.ecommerce.review.repository;

import com.ecommerce.review.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    @Query(
            value = """
            SELECT r FROM Review r
            JOIN FETCH r.user
            WHERE r.product.id = :productId
            ORDER BY r.createdAt DESC
        """,
            countQuery = """
            SELECT COUNT(r) FROM Review r
            WHERE r.product.id = :productId
        """
    )
    Page<Review> findByProductIdWithUserOrderByCreatedAtDesc(UUID productId, Pageable pageable);

    boolean existsByUserIdAndProductId(UUID userId, UUID productId);

    Optional<Review> findByUserIdAndProductId(UUID userId, UUID productId);

    int countByProductId(UUID productId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.product.id = :productId")
    Optional<Double> findAverageRatingByProductId(UUID productId);

    @Query("SELECT COUNT(o) > 0 FROM Order o JOIN o.items i " +
            "WHERE o.user.id = :userId AND i.product.id = :productId AND o.status = 'PAID'")
    boolean hasUserPurchasedProduct(UUID userId, UUID productId);
}