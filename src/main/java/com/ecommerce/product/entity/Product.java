// File: src/main/java/com/ecommerce/product/entity/Product.java
package com.ecommerce.product.entity;

import com.ecommerce.category.entity.Category;
import io.hypersistence.utils.hibernate.type.array.ListArrayType;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Product entity.
 *
 * Design choices:
 * - specs: JSONB column mapped as Map<String,Object> via hypersistence-utils.
 *   This allows per-category flexible attributes without schema changes.
 *   Examples: {ram_gb: 16, cpu: "i9-13900H"} for laptops, {battery_mah: 5000} for phones.
 *
 * - imageUrls: TEXT[] PostgreSQL array (ordered list of image URLs).
 *   Alternative was a separate product_images table, but array is simpler
 *   for read-heavy catalog use and images are always fetched with the product.
 *
 * - search_vector: managed by DB trigger (see migration V2), not mapped as JPA field.
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String brand;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /**
     * Flexible JSONB specs: per-category product attributes.
     * Stored as Map<String, Object> in Java, JSONB in PostgreSQL.
     * Queryable via native queries with jsonb operators.
     */
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> specs = new HashMap<>();

    /**
     * PostgreSQL TEXT[] array for ordered image URLs.
     * Design choice: array over separate table for simplicity
     * when images are always fetched with product.
     */
    @Type(ListArrayType.class)
    @Column(name = "image_urls", columnDefinition = "text[]")
    @Builder.Default
    private List<String> imageUrls = new ArrayList<>();

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
