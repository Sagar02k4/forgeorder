package com.sagar.forgeorder.orders.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    @Test
    void newOrderStartsInDraftStatus() {
        Order order = new Order(UUID.randomUUID(), new BigDecimal("100.00"), new BigDecimal("18.00"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DRAFT);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("118.00");
    }

    @Test
    void transitionToValidStateUpdatesStatusAndReturnsMatchingAuditEvent() {
        Order order = new Order(UUID.randomUUID(), new BigDecimal("100.00"), new BigDecimal("18.00"));
        order.transitionTo(OrderStatus.CREATED, "SYSTEM_WORKER", null, "corr-123", null, "initial creation");
        // First move it to CREATED (DRAFT -> CREATED is the only valid first step)

        OrderAuditEvent event = order.transitionTo(
                OrderStatus.INVENTORY_RESERVATION_PENDING,
                "SYSTEM_WORKER",
                null,
                "corr-123",
                null,
                "reservation requested"
        );

        assertThat(order.getStatus()).isEqualTo(OrderStatus.INVENTORY_RESERVATION_PENDING);
        assertThat(event.getOrderId()).isEqualTo(order.getId());
        assertThat(event.getPreviousState()).isEqualTo(OrderStatus.CREATED);
        assertThat(event.getNewState()).isEqualTo(OrderStatus.INVENTORY_RESERVATION_PENDING);
        assertThat(event.getCorrelationId()).isEqualTo("corr-123");
    }

    @Test
    void transitionToInvalidStateThrowsAndDoesNotChangeStatus() {
        Order order = new Order(UUID.randomUUID(), new BigDecimal("100.00"), new BigDecimal("18.00"));

        assertThatThrownBy(() ->
                order.transitionTo(OrderStatus.CONFIRMED, "SYSTEM_WORKER", null, "corr-123", null, "invalid jump")
        ).isInstanceOf(InvalidOrderStateTransitionException.class);

        // status should remain unchanged since the transition was rejected
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DRAFT);
    }
}