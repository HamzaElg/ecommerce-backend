// File: src/main/java/com/ecommerce/analytics/repository/AnalyticsRepository.java
package com.ecommerce.analytics.repository;

import com.ecommerce.analytics.dto.DailySalesDto;
import com.ecommerce.analytics.dto.TopProductDto;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Analytics queries using JPQL and native SQL.
 * These are read-only and admin-only.
 */
public interface AnalyticsRepository extends Repository<com.ecommerce.order.entity.Order, java.util.UUID> {

    /** Daily sales aggregation */
    @Query(value = """
        SELECT DATE(o.created_at) as date,
               COUNT(o.id) as orderCount,
               SUM(o.total_amount) as revenue
        FROM orders o
        WHERE o.status = 'PAID'
          AND o.created_at >= :from AND o.created_at <= :to
        GROUP BY DATE(o.created_at)
        ORDER BY DATE(o.created_at)
        """, nativeQuery = true)
    List<Object[]> findDailySalesRaw(@Param("from") Instant from, @Param("to") Instant to);

    /** Top selling products by quantity */
    @Query(value = """
        SELECT oi.product_id, oi.product_name_snapshot,
               SUM(oi.quantity) as totalSold,
               SUM(oi.quantity * oi.unit_price) as revenue
        FROM order_items oi
        JOIN orders o ON oi.order_id = o.id
        WHERE o.status = 'PAID'
          AND o.created_at >= :from
        GROUP BY oi.product_id, oi.product_name_snapshot
        ORDER BY totalSold DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findTopProductsRaw(@Param("from") Instant from, @Param("limit") int limit);
}
