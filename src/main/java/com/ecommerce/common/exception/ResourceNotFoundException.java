// File: src/main/java/com/ecommerce/common/exception/ResourceNotFoundException.java
package com.ecommerce.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends EcommerceException {
    public ResourceNotFoundException(String resourceType, Object id) {
        super("RESOURCE_NOT_FOUND",
              String.format("%s with id '%s' not found", resourceType, id),
              HttpStatus.NOT_FOUND);
    }
}
