// File: src/main/java/com/ecommerce/common/exception/EcommerceException.java
package com.ecommerce.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base exception for all business logic errors in this application.
 * Carries an error code (for machine-readable frontend handling) and HTTP status.
 */
@Getter
public class EcommerceException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus httpStatus;

    public EcommerceException(String errorCode, String message, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
}
