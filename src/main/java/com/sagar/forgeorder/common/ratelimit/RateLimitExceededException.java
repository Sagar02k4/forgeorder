package com.sagar.forgeorder.common.ratelimit;

public class RateLimitExceededException extends RuntimeException {

    private final String errorCode;

    public RateLimitExceededException(String message) {
        super(message);
        this.errorCode = "RATE_LIMIT_EXCEEDED";
    }

    public String getErrorCode() {
        return errorCode;
    }
}