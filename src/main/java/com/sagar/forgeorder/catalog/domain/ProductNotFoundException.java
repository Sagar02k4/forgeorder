package com.sagar.forgeorder.catalog.domain;

import java.util.UUID;

public class ProductNotFoundException extends RuntimeException {

    private final String errorCode;
    private final UUID productId;

    public ProductNotFoundException(UUID productId) {
        super("Product not found: " + productId);
        this.errorCode = "PRODUCT_NOT_FOUND";
        this.productId = productId;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public UUID getProductId() {
        return productId;
    }
}