package com.ecommerce.category.service;

import com.ecommerce.category.dto.CategoryRequest;
import com.ecommerce.category.dto.CategoryResponse;
import com.ecommerce.category.entity.Category;
import com.ecommerce.category.repository.CategoryRepository;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> getActiveCategories() {
        return categoryRepository.findByIsActiveTrue().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getRootCategories() {
        return categoryRepository.findByParentIsNullAndIsActiveTrue().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getChildren(UUID parentId) {
        categoryRepository.findByIdAndIsActiveTrue(parentId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", parentId));

        return categoryRepository.findByParentIdAndIsActiveTrue(parentId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getById(UUID id) {
        Category category = categoryRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));

        return toResponse(category);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllForAdmin() {
        return categoryRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        validateSlugUnique(request.slug(), null);

        Category parent = null;

        if (request.parentId() != null) {
            parent = categoryRepository.findByIdAndIsActiveTrue(request.parentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent category", request.parentId()));
        }

        Category category = Category.builder()
                .name(request.name())
                .slug(request.slug())
                .parent(parent)
                .isActive(true)
                .build();

        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));

        validateSlugUnique(request.slug(), id);
        validateParent(id, request.parentId());

        Category parent = null;

        if (request.parentId() != null) {
            parent = categoryRepository.findByIdAndIsActiveTrue(request.parentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent category", request.parentId()));
        }

        category.setName(request.name());
        category.setSlug(request.slug());
        category.setParent(parent);

        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public void deactivate(UUID id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));

        category.deactivate();
        categoryRepository.save(category);
    }

    @Transactional
    public CategoryResponse activate(UUID id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));

        category.activate();
        return toResponse(categoryRepository.save(category));
    }

    private void validateSlugUnique(String slug, UUID currentCategoryId) {
        categoryRepository.findBySlug(slug).ifPresent(existing -> {
            if (currentCategoryId == null || !existing.getId().equals(currentCategoryId)) {
                throw new BusinessException(
                        "CATEGORY_SLUG_EXISTS",
                        "Category slug already exists",
                        HttpStatus.CONFLICT
                );
            }
        });
    }

    private void validateParent(UUID categoryId, UUID parentId) {
        if (parentId == null) return;

        if (parentId.equals(categoryId)) {
            throw new BusinessException(
                    "INVALID_CATEGORY_PARENT",
                    "A category cannot be its own parent",
                    HttpStatus.BAD_REQUEST
            );
        }

        Category current = categoryRepository.findByIdAndIsActiveTrue(parentId)
                .orElseThrow(() -> new ResourceNotFoundException("Parent category", parentId));

        while (current.getParent() != null) {
            UUID currentParentId = current.getParent().getId();

            if (currentParentId.equals(categoryId)) {
                throw new BusinessException(
                        "INVALID_CATEGORY_HIERARCHY",
                        "A category cannot be moved under one of its own descendants",
                        HttpStatus.BAD_REQUEST
                );
            }

            current = current.getParent();
        }
    }

    private CategoryResponse toResponse(Category category) {
        Category parent = category.getParent();

        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                parent != null ? parent.getId() : null,
                parent != null ? parent.getName() : null,
                category.isActive(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }
}