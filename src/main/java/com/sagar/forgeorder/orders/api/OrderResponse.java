package com.sagar.forgeorder.orders.api;

import com.sagar.forgeorder.orders.domain.Order;
import com.sagar.forgeorder.orders.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID customerId,
        UUID productId,
        int quantity,
        OrderStatus status,
        BigDecimal subtotal,
        BigDecimal tax,
        BigDecimal totalAmount,
        Instant createdAt,
        Instant updatedAt,
        InventorySummary inventory,
        PaymentSummary payment
) {

    public record InventorySummary(
            int availableQuantity,
            int reservedQuantity
    ) {
    }

    public record PaymentSummary(
            String status,
            String providerPaymentId,
            String declineReason
    ) {
    }

    public static OrderResponse from(Order order, InventorySummary inventory, PaymentSummary payment) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getProductId(),
                order.getQuantity(),
                order.getStatus(),
                order.getSubtotal(),
                order.getTax(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                inventory,
                payment
        );
    }
}