// File: src/main/java/com/ecommerce/auth/dto/AuthResponse.java
package com.ecommerce.auth.dto;

import java.util.UUID;

public record AuthResponse(
    UUID userId,
    String email,
    String role,
    String accessToken,
    String refreshToken
) {}
