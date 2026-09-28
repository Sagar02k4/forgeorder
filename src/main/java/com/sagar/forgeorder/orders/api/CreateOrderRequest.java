package com.sagar.forgeorder.orders.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateOrderRequest(

        @NotNull(message = "customerId is required")
        UUID customerId,

        @NotNull(message = "subtotal is required")
        @DecimalMin(value = "0.00", message = "subtotal must not be negative")
        BigDecimal subtotal,

        @NotNull(message = "tax is required")
        @DecimalMin(value = "0.00", message = "tax must not be negative")
        BigDecimal tax
) {
}