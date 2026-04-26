package com.ecommerce.product.dto;

public record ProductImportError(
        int row,
        String message
) {}