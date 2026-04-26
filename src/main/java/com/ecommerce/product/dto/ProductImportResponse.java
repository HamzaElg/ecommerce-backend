package com.ecommerce.product.dto;

import java.util.List;

public record ProductImportResponse(
        int totalRows,
        int created,
        int failed,
        List<ProductImportError> errors
) {}