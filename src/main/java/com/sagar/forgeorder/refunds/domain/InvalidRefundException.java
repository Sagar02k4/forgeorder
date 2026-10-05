package com.sagar.forgeorder.refunds.domain;

public class InvalidRefundException extends RuntimeException {

    private final String errorCode;

    public InvalidRefundException(String message) {
        super(message);
        this.errorCode = "INVALID_REFUND";
    }

    public String getErrorCode() {
        return errorCode;
    }
}