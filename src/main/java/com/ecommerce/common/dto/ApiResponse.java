// File: src/main/java/com/ecommerce/common/dto/ApiResponse.java
package com.ecommerce.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Standard API response envelope for all endpoints.
 *
 * Success: { status: "success", data: {...}, pagination: {...} }
 * Error:   { status: "error", code: "...", message: "...", timestamp: "..." }
 *
 * Design choice: using a generic wrapper improves frontend consistency
 * and makes error handling predictable.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final String status;
    private final T data;
    private final PaginationInfo pagination;
    private final String code;
    private final String message;
    private final Instant timestamp;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .status("success")
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> success(T data, PaginationInfo pagination) {
        return ApiResponse.<T>builder()
                .status("success")
                .data(data)
                .pagination(pagination)
                .build();
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return ApiResponse.<T>builder()
                .status("error")
                .code(code)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }
}
