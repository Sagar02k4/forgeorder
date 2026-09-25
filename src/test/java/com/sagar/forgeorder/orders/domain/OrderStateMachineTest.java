package com.sagar.forgeorder.orders.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStateMachineTest {

    @Test
    void allowsValidForwardTransition() {
        boolean result = OrderStateMachine.canTransition(
                OrderStatus.CREATED,
                OrderStatus.INVENTORY_RESERVATION_PENDING
        );

        assertThat(result).isTrue();
    }

    @Test
    void rejectsIllegalReverseTransitionFromConfirmedToPaymentPending() {
        boolean result = OrderStateMachine.canTransition(
                OrderStatus.CONFIRMED,
                OrderStatus.PAYMENT_PENDING
        );

        assertThat(result).isFalse();
    }

    @Test
    void validateTransitionThrowsExceptionForIllegalTransition() {
        assertThatThrownBy(() ->
                OrderStateMachine.validateTransition(OrderStatus.CONFIRMED, OrderStatus.PAYMENT_PENDING)
        )
                .isInstanceOf(InvalidOrderStateTransitionException.class)
                .hasMessageContaining("CONFIRMED")
                .hasMessageContaining("PAYMENT_PENDING");
    }

    @Test
    void validateTransitionDoesNotThrowForLegalTransition() {
        // Should complete without throwing — no assertion needed for "no exception",
        // the test simply fails if an exception is thrown.
        OrderStateMachine.validateTransition(OrderStatus.CREATED, OrderStatus.INVENTORY_RESERVATION_PENDING);
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void terminalStatesAllowNoOutgoingTransitions(OrderStatus anyStatus) {
        if (anyStatus == OrderStatus.CANCELLED || anyStatus == OrderStatus.REFUNDED) {
            boolean canGoAnywhere = false;
            for (OrderStatus target : OrderStatus.values()) {
                if (OrderStateMachine.canTransition(anyStatus, target)) {
                    canGoAnywhere = true;
                }
            }
            assertThat(canGoAnywhere).isFalse();
        }
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void noStateCanTransitionToItself(OrderStatus anyStatus) {
        boolean canLoopToSelf = OrderStateMachine.canTransition(anyStatus, anyStatus);

        assertThat(canLoopToSelf).isFalse();
    }
}