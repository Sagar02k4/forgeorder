package com.sagar.forgeorder.webhooks.domain;

public class InvalidWebhookSignatureException extends RuntimeException {

    private final String errorCode;

    public InvalidWebhookSignatureException(String message) {
        super(message);
        this.errorCode = "INVALID_WEBHOOK_SIGNATURE";
    }

    public String getErrorCode() {
        return errorCode;
    }
}