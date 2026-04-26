package com.ecommerce.product.controller;

import com.ecommerce.common.dto.ApiResponse;
import com.ecommerce.product.dto.ProductImportResponse;
import com.ecommerce.product.service.ProductImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Products")
public class ProductImportController {

    private final ProductImportService productImportService;

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import products from CSV")
    public ResponseEntity<ApiResponse<ProductImportResponse>> importProducts(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(productImportService.importProducts(file)));
    }
}