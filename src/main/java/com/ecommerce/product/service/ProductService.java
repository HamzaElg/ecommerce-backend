// File: src/main/java/com/ecommerce/product/service/ProductService.java
package com.ecommerce.product.service;

import com.ecommerce.category.entity.Category;
import com.ecommerce.category.repository.CategoryRepository;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.inventory.entity.Inventory;
import com.ecommerce.inventory.repository.InventoryRepository;
import com.ecommerce.product.dto.AdminProductCreateRequest;
import com.ecommerce.product.dto.ProductRequest;
import com.ecommerce.product.dto.ProductResponse;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
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

    private static final int MAX_PAGE_SIZE = 100;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryRepository inventoryRepository;
    private final ReviewRepository reviewRepository;

    @Cacheable(
            value = "product-search",
            key = "'q=' + #q + '|category=' + #categoryId + '|brand=' + #brand + '|minPrice=' + #minPrice + '|maxPrice=' + #maxPrice + '|minRam=' + #minRam + '|page=' + #page + '|size=' + #size"
    )
    @Transactional(readOnly = true)
    public Page<ProductResponse> searchProducts(
            String q,
            UUID categoryId,
            String brand,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Integer minRam,
            int page,
            int size) {

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Pageable pageable = PageRequest.of(safePage, safeSize);

        Page<Product> products = productRepository.searchProducts(
                normalize(q),
                categoryId,
                normalize(brand),
                minPrice,
                maxPrice,
                minRam,
                pageable
        );

        return products.map(this::toResponse);
    }

    @Cacheable(value = "product-detail", key = "#id")
    @Transactional(readOnly = true)
    public ProductResponse getById(UUID id) {
        Product product = productRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        return toResponse(product);
    }

    @Transactional
    @CacheEvict(value = "product-search", allEntries = true)
    public ProductResponse create(AdminProductCreateRequest request) {
        ProductRequest productRequest = new ProductRequest(
                request.name(),
                request.brand(),
                request.description(),
                request.price(),
                request.categoryId(),
                request.specs(),
                request.imageUrls()
        );

        return createWithInitialStockInternal(productRequest, request.initialStockQty());
    }

    /**
     * Used by CSV import.
     *
     * Important:
     * - This method evicts product-search because CSV import creates products.
     * - It delegates to a private internal method to avoid relying on Spring self-invocation.
     */
    @Transactional
    @CacheEvict(value = "product-search", allEntries = true)
    public ProductResponse createWithInitialStock(ProductRequest request, int initialStockQty) {
        return createWithInitialStockInternal(request, initialStockQty);
    }

    private ProductResponse createWithInitialStockInternal(ProductRequest request, int initialStockQty) {
        if (initialStockQty < 0) {
            throw new BusinessException(
                    "INVALID_STOCK_QUANTITY",
                    "Initial stock cannot be negative",
                    HttpStatus.BAD_REQUEST
            );
        }

        Category category = categoryRepository.findByIdAndIsActiveTrue(request.categoryId())
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

        Inventory inventory = Inventory.builder()
                .productId(product.getId())
                .product(product)
                .stockQty(initialStockQty)
                .reservedQty(0)
                .build();

        inventoryRepository.save(inventory);

        log.info(
                "Product created: id={}, name={}, initialStock={}",
                product.getId(),
                product.getName(),
                initialStockQty
        );

        return toResponse(product);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "product-detail", key = "#id"),
            @CacheEvict(value = "product-search", allEntries = true)
    })
    public ProductResponse update(UUID id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        Category category = categoryRepository.findByIdAndIsActiveTrue(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", request.categoryId()));

        product.setName(request.name());
        product.setBrand(request.brand());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setCategory(category);

        product.setSpecs(request.specs() != null ? request.specs() : new HashMap<>());
        product.setImageUrls(request.imageUrls() != null ? request.imageUrls() : new ArrayList<>());

        product = productRepository.save(product);

        log.info("Product updated: id={}", id);

        return toResponse(product);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "product-detail", key = "#id"),
            @CacheEvict(value = "product-search", allEntries = true)
    })
    public void delete(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        product.setActive(false);
        productRepository.save(product);

        log.info("Product soft-deleted: id={}", id);
    }

    private ProductResponse toResponse(Product product) {
        int availableStock = inventoryRepository.findByProductId(product.getId())
                .map(Inventory::getAvailableQty)
                .orElse(0);

        double avgRating = reviewRepository.findAverageRatingByProductId(product.getId())
                .orElse(0.0);

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

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}