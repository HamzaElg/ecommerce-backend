// File: src/main/java/com/ecommerce/common/dto/PaginationInfo.java
package com.ecommerce.common.dto;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

@Getter
@Builder
public class PaginationInfo {
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;

    public static PaginationInfo from(Page<?> page) {
        return PaginationInfo.builder()
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }
}
