package com.ecommerce.category.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CategoryRequest(
        @NotBlank String name,
        @NotBlank String slug,
        UUID parentId
) {}