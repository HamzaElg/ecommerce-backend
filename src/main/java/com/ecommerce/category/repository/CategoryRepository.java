// File: src/main/java/com/ecommerce/category/repository/CategoryRepository.java
package com.ecommerce.category.repository;

import com.ecommerce.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    List<Category> findByParentIsNull();
    List<Category> findByParentId(UUID parentId);
    Optional<Category> findBySlug(String slug);
    boolean existsBySlug(String slug);
}
