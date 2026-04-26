package com.ecommerce.category.repository;

import com.ecommerce.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    // Public/category browsing: active only
    List<Category> findByIsActiveTrue();

    List<Category> findByParentIsNullAndIsActiveTrue();

    List<Category> findByParentIdAndIsActiveTrue(UUID parentId);

    Optional<Category> findByIdAndIsActiveTrue(UUID id);

    // Admin/internal: active + inactive
    List<Category> findByParentIsNull();

    List<Category> findByParentId(UUID parentId);

    Optional<Category> findBySlug(String slug);

    boolean existsBySlug(String slug);
}