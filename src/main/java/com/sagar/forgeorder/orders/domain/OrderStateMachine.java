package com.sagar.forgeorder.orders.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = buildTransitions();

    private OrderStateMachine() {
        // utility class — no instances allowed
    }

    private static Map<OrderStatus, Set<OrderStatus>> buildTransitions() {
        Map<OrderStatus, Set<OrderStatus>> map = new EnumMap<>(OrderStatus.class);

        map.put(OrderStatus.DRAFT, EnumSet.of(OrderStatus.CREATED));

        map.put(OrderStatus.CREATED, EnumSet.of(
                OrderStatus.INVENTORY_RESERVATION_PENDING,
                OrderStatus.CANCELLATION_REQUESTED
        ));

        map.put(OrderStatus.INVENTORY_RESERVATION_PENDING, EnumSet.of(
                OrderStatus.INVENTORY_RESERVED,
                OrderStatus.INVENTORY_UNAVAILABLE
        ));

        map.put(OrderStatus.INVENTORY_RESERVED, EnumSet.of(
                OrderStatus.PAYMENT_PENDING,
                OrderStatus.CANCELLATION_REQUESTED
        ));

        map.put(OrderStatus.INVENTORY_UNAVAILABLE, EnumSet.of(
                OrderStatus.CANCELLED
        ));

        map.put(OrderStatus.PAYMENT_PENDING, EnumSet.of(
                OrderStatus.PAYMENT_SUCCEEDED,
                OrderStatus.PAYMENT_FAILED,
                OrderStatus.PAYMENT_STATUS_UNKNOWN,
                OrderStatus.CANCELLATION_REQUESTED
        ));

        map.put(OrderStatus.PAYMENT_SUCCEEDED, EnumSet.of(
                OrderStatus.CONFIRMED
        ));

        map.put(OrderStatus.PAYMENT_FAILED, EnumSet.of(
                OrderStatus.INVENTORY_RELEASE_PENDING
        ));

        map.put(OrderStatus.PAYMENT_STATUS_UNKNOWN, EnumSet.of(
                OrderStatus.RECONCILIATION_REQUIRED
        ));

        map.put(OrderStatus.RECONCILIATION_REQUIRED, EnumSet.of(
                OrderStatus.PAYMENT_SUCCEEDED,
                OrderStatus.PAYMENT_FAILED
        ));

        map.put(OrderStatus.INVENTORY_RELEASE_PENDING, EnumSet.of(
                OrderStatus.CANCELLED
        ));

        map.put(OrderStatus.CANCELLATION_REQUESTED, EnumSet.of(
                OrderStatus.CANCELLED
        ));

        map.put(OrderStatus.CONFIRMED, EnumSet.of(
                OrderStatus.FULFILLED,
                OrderStatus.REFUND_PENDING
        ));

        map.put(OrderStatus.FULFILLED, EnumSet.of(
                OrderStatus.REFUND_PENDING
        ));

        map.put(OrderStatus.REFUND_PENDING, EnumSet.of(
                OrderStatus.REFUNDED
        ));

        // Terminal states — explicitly empty, not missing from the map.
        map.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
        map.put(OrderStatus.REFUNDED, EnumSet.noneOf(OrderStatus.class));

        return map;
    }

    public static boolean canTransition(OrderStatus from, OrderStatus to) {
        Set<OrderStatus> allowedNextStates = ALLOWED_TRANSITIONS.get(from);
        return allowedNextStates != null && allowedNextStates.contains(to);
    }

    public static void validateTransition(OrderStatus from, OrderStatus to) {
        if (!canTransition(from, to)) {
            throw new InvalidOrderStateTransitionException(from, to);
        }
    }
}