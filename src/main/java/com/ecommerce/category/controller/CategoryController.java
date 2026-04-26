package com.ecommerce.category.controller;

import com.ecommerce.category.dto.CategoryRequest;
import com.ecommerce.category.dto.CategoryResponse;
import com.ecommerce.category.service.CategoryService;
import com.ecommerce.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Categories")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping("/api/v1/categories")
    @Operation(summary = "Get active categories")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getActiveCategories() {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getActiveCategories()));
    }

    @GetMapping("/api/v1/categories/root")
    @Operation(summary = "Get active root categories")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getRootCategories() {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getRootCategories()));
    }

    @GetMapping("/api/v1/categories/{id}")
    @Operation(summary = "Get active category by ID")
    public ResponseEntity<ApiResponse<CategoryResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getById(id)));
    }

    @GetMapping("/api/v1/categories/{id}/children")
    @Operation(summary = "Get active children of category")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getChildren(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getChildren(id)));
    }

    @GetMapping("/api/v1/admin/categories")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Admin: get all categories including inactive")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getAllForAdmin() {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getAllForAdmin()));
    }

    @PostMapping("/api/v1/admin/categories")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Admin: create category")
    public ResponseEntity<ApiResponse<CategoryResponse>> create(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(categoryService.create(request)));
    }

    @PutMapping("/api/v1/admin/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Admin: update category")
    public ResponseEntity<ApiResponse<CategoryResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.update(id, request)));
    }

    @DeleteMapping("/api/v1/admin/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Admin: soft delete category")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable UUID id) {
        categoryService.deactivate(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PatchMapping("/api/v1/admin/categories/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Admin: reactivate category")
    public ResponseEntity<ApiResponse<CategoryResponse>> activate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.activate(id)));
    }
}