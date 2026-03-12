// File: src/main/java/com/ecommerce/product/service/ProductService.java
package com.ecommerce.product.service;

import com.ecommerce.category.entity.Category;
import com.ecommerce.category.repository.CategoryRepository;
import com.ecommerce.common.dto.PaginationInfo;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.inventory.entity.Inventory;
import com.ecommerce.inventory.repository.InventoryRepository;
import com.ecommerce.product.dto.ProductRequest;
import com.ecommerce.product.dto.ProductResponse;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryRepository inventoryRepository;
    private final ReviewRepository reviewRepository;

    /**
     * Search products with multiple optional filters.
     * Results are cached in Redis for 5 minutes.
     * Cache is evicted on product create/update/delete.
     */
    @Cacheable(value = "product-search", key = "#root.methodName + #q + #categoryId + #brand + #minPrice + #maxPrice + #minRam + #page + #size")
    public Page<ProductResponse> searchProducts(
            String q, String categoryId, String brand,
            BigDecimal minPrice, BigDecimal maxPrice, Integer minRam,
            int page, int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 100)); // Cap at 100 per page
        Page<Product> products = productRepository.searchProducts(
                q, categoryId, brand, minPrice, maxPrice, minRam, pageable
        );
        return products.map(this::toResponse);
    }

    /**
     * Get product detail including specs, images, stock, and review summary.
     * Cached individually per product ID.
     */
    @Cacheable(value = "product-detail", key = "#id")
    public ProductResponse getById(UUID id) {
        Product product = productRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        return toResponse(product);
    }

    /** Admin: create product and initialize inventory at 0 */
    @Transactional
    @CacheEvict(value = {"product-search", "product-detail"}, allEntries = true)
    public ProductResponse create(ProductRequest request) {
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", request.categoryId()));

        Product product = Product.builder()
                .name(request.name())
                .brand(request.brand())
                .description(request.description())
                .price(request.price())
                .category(category)
                .specs(request.specs() != null ? request.specs() : new HashMap<>())
                .imageUrls(request.imageUrls() != null ? request.imageUrls() : new ArrayList<>())
                .active(true)
                .build();

        product = productRepository.save(product);

        // Initialize inventory record with 0 stock
        Inventory inventory = Inventory.builder()
                .productId(product.getId())
                .product(product)
                .stockQty(0)
                .reservedQty(0)
                .build();
        inventoryRepository.save(inventory);

        log.info("Product created: id={}, name={}", product.getId(), product.getName());
        return toResponse(product);
    }

    /** Admin: update product fields */
    @Transactional
    @CacheEvict(value = {"product-search", "product-detail"}, key = "#id")
    public ProductResponse update(UUID id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", request.categoryId()));

        product.setName(request.name());
        product.setBrand(request.brand());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setCategory(category);
        if (request.specs() != null) product.setSpecs(request.specs());
        if (request.imageUrls() != null) product.setImageUrls(request.imageUrls());

        product = productRepository.save(product);
        log.info("Product updated: id={}", id);
        return toResponse(product);
    }

    /** Admin: soft delete (isActive = false) - preserves order history */
    @Transactional
    @CacheEvict(value = {"product-search", "product-detail"}, key = "#id")
    public void delete(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        product.setActive(false);
        productRepository.save(product);
        log.info("Product soft-deleted: id={}", id);
    }

    /** Map Product entity to response DTO, enriching with inventory and review data */
    private ProductResponse toResponse(Product product) {
        int availableStock = inventoryRepository.findByProductId(product.getId())
                .map(Inventory::getAvailableQty).orElse(0);

        double avgRating = reviewRepository.findAverageRatingByProductId(product.getId()).orElse(0.0);
        int reviewCount = reviewRepository.countByProductId(product.getId());

        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getBrand(),
                product.getDescription(),
                product.getPrice(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.getSpecs(),
                product.getImageUrls(),
                product.isActive(),
                availableStock,
                avgRating,
                reviewCount,
                product.getCreatedAt()
        );
    }
}
