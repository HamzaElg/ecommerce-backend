// File: src/main/java/com/ecommerce/common/exception/BusinessException.java
package com.ecommerce.common.exception;

import org.springframework.http.HttpStatus;

/** General business rule violation */
public class BusinessException extends EcommerceException {
    public BusinessException(String code, String message) {
        super(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
    public BusinessException(String code, String message, HttpStatus status) {
        super(code, message, status);
    }
}
