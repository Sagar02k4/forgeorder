package com.sagar.forgeorder.orders.domain;

import java.util.UUID;

public class OrderNotFoundException extends RuntimeException {

    private final String errorCode;
    private final UUID orderId;

    public OrderNotFoundException(UUID orderId) {
        super("Order not found: " + orderId);
        this.errorCode = "ORDER_NOT_FOUND";
        this.orderId = orderId;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public UUID getOrderId() {
        return orderId;
    }
}