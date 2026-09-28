package com.sagar.forgeorder.common.idempotency;

public class IdempotencyOutcome {

    public enum Type {
        PROCEED,
        IN_PROGRESS,
        CONFLICT,
        ALREADY_COMPLETED
    }

    private final Type type;
    private final Integer storedStatusCode;
    private final String storedResponseBody;

    private IdempotencyOutcome(Type type, Integer storedStatusCode, String storedResponseBody) {
        this.type = type;
        this.storedStatusCode = storedStatusCode;
        this.storedResponseBody = storedResponseBody;
    }

    public static IdempotencyOutcome proceed() {
        return new IdempotencyOutcome(Type.PROCEED, null, null);
    }

    public static IdempotencyOutcome inProgress() {
        return new IdempotencyOutcome(Type.IN_PROGRESS, null, null);
    }

    public static IdempotencyOutcome conflict() {
        return new IdempotencyOutcome(Type.CONFLICT, null, null);
    }

    public static IdempotencyOutcome alreadyCompleted(Integer statusCode, String responseBody) {
        return new IdempotencyOutcome(Type.ALREADY_COMPLETED, statusCode, responseBody);
    }

    public Type getType() {
        return type;
    }

    public Integer getStoredStatusCode() {
        return storedStatusCode;
    }

    public String getStoredResponseBody() {
        return storedResponseBody;
    }
}