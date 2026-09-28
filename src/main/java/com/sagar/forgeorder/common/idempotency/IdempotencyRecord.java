package com.sagar.forgeorder.common.idempotency;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Entity
@Table(name = "idempotency_records")
@Getter
public class IdempotencyRecord {

    @Id
    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "operation_type", nullable = false)
    private String operationType;

    @Column(name = "actor_id")
    private String actorId;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private IdempotencyStatus status;

    @Column(name = "response_status_code")
    private Integer responseStatusCode;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Version
    private Long version;

    protected IdempotencyRecord() {
        // required by JPA
    }

    public IdempotencyRecord(String idempotencyKey,
                             String operationType,
                             String actorId,
                             String requestHash,
                             Instant expiresAt) {
        this.idempotencyKey = idempotencyKey;
        this.operationType = operationType;
        this.actorId = actorId;
        this.requestHash = requestHash;
        this.status = IdempotencyStatus.IN_PROGRESS;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public void markSucceeded(int statusCode, String responseBody) {
        this.status = IdempotencyStatus.SUCCEEDED;
        this.responseStatusCode = statusCode;
        this.responseBody = responseBody;
    }

    public void markFailed(int statusCode, String responseBody) {
        this.status = IdempotencyStatus.FAILED;
        this.responseStatusCode = statusCode;
        this.responseBody = responseBody;
    }
}