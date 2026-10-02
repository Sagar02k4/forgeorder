package com.sagar.forgeorder.payments.api;

import com.sagar.forgeorder.orders.domain.OrderStatus;

import java.util.UUID;

public record PaymentResponse(
        UUID orderId,
        OrderStatus orderStatus,
        String message
) {
}