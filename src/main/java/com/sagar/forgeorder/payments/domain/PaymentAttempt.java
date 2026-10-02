package com.sagar.forgeorder.payments.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_attempts")
@Getter
public class PaymentAttempt {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "provider", nullable = false)
    private String provider;

    @Column(name = "provider_payment_id")
    private String providerPaymentId;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "decline_reason")
    private String declineReason;

    @Column(name = "raw_response", columnDefinition = "TEXT")
    private String rawResponse;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected PaymentAttempt() {
        // required by JPA
    }

    public PaymentAttempt(UUID orderId, String provider, String idempotencyKey, BigDecimal amount) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.provider = provider;
        this.idempotencyKey = idempotencyKey;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markSucceeded(String providerPaymentId, String rawResponse) {
        this.status = PaymentStatus.SUCCEEDED;
        this.providerPaymentId = providerPaymentId;
        this.rawResponse = rawResponse;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String declineReason, String rawResponse) {
        this.status = PaymentStatus.FAILED;
        this.declineReason = declineReason;
        this.rawResponse = rawResponse;
        this.updatedAt = Instant.now();
    }

    public void markUnknown(String rawResponse) {
        this.status = PaymentStatus.UNKNOWN;
        this.rawResponse = rawResponse;
        this.updatedAt = Instant.now();
    }
}