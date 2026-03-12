// File: src/main/java/com/ecommerce/common/exception/InsufficientStockException.java
package com.ecommerce.common.exception;

import org.springframework.http.HttpStatus;

public class InsufficientStockException extends EcommerceException {
    public InsufficientStockException(String productName, int available, int requested) {
        super("INSUFFICIENT_STOCK",
              String.format("Only %d units available for '%s', but %d requested", available, productName, requested),
              HttpStatus.CONFLICT);
    }
}
