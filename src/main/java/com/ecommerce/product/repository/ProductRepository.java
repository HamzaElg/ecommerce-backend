// File: src/main/java/com/ecommerce/product/repository/ProductRepository.java
package com.ecommerce.product.repository;

import com.ecommerce.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByIdAndActiveTrue(UUID id);

    /**
     * Full product search with optional filters.
     *
     * Uses native PostgreSQL query for:
     * - Full-text search via tsvector (GIN indexed)
     * - JSONB spec filtering (e.g. ram_gb >= minRam)
     * - Category, brand, price range filters
     * - All params are optional (COALESCE pattern)
     *
     * COALESCE pattern: "(:param IS NULL OR condition)" makes each filter optional.
     */
    @Query(value = """
        SELECT p.* FROM products p
        JOIN categories c ON p.category_id = c.id
        WHERE p.is_active = true
          AND (:query IS NULL OR p.search_vector @@ plainto_tsquery('english', :query)
               OR p.name ILIKE '%' || :query || '%')
          AND (:categoryId IS NULL OR p.category_id = :categoryId::uuid
               OR c.parent_id = :categoryId::uuid)
          AND (:brand IS NULL OR LOWER(p.brand) = LOWER(:brand))
          AND (:minPrice IS NULL OR p.price >= :minPrice)
          AND (:maxPrice IS NULL OR p.price <= :maxPrice)
          AND (:minRam IS NULL OR (p.specs->>'ram_gb')::integer >= :minRam)
        ORDER BY
          CASE WHEN :query IS NOT NULL
               THEN ts_rank(p.search_vector, plainto_tsquery('english', :query))
               ELSE 0 END DESC,
          p.created_at DESC
        """,
        countQuery = """
        SELECT COUNT(*) FROM products p
        JOIN categories c ON p.category_id = c.id
        WHERE p.is_active = true
          AND (:query IS NULL OR p.search_vector @@ plainto_tsquery('english', :query)
               OR p.name ILIKE '%' || :query || '%')
          AND (:categoryId IS NULL OR p.category_id = :categoryId::uuid
               OR c.parent_id = :categoryId::uuid)
          AND (:brand IS NULL OR LOWER(p.brand) = LOWER(:brand))
          AND (:minPrice IS NULL OR p.price >= :minPrice)
          AND (:maxPrice IS NULL OR p.price <= :maxPrice)
          AND (:minRam IS NULL OR (p.specs->>'ram_gb')::integer >= :minRam)
        """,
        nativeQuery = true)
    Page<Product> searchProducts(
        @Param("query") String query,
        @Param("categoryId") String categoryId,
        @Param("brand") String brand,
        @Param("minPrice") BigDecimal minPrice,
        @Param("maxPrice") BigDecimal maxPrice,
        @Param("minRam") Integer minRam,
        Pageable pageable
    );
}
