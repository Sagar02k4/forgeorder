package com.sagar.forgeorder.orders.domain;

public class InvalidOrderStateTransitionException extends RuntimeException {

    private final String errorCode;
    private final OrderStatus fromState;
    private final OrderStatus toState;

    public InvalidOrderStateTransitionException(OrderStatus fromState, OrderStatus toState) {
        super("Cannot transition order from " + fromState + " to " + toState);
        this.errorCode = "INVALID_STATE_TRANSITION";
        this.fromState = fromState;
        this.toState = toState;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public OrderStatus getFromState() {
        return fromState;
    }

    public OrderStatus getToState() {
        return toState;
    }
}