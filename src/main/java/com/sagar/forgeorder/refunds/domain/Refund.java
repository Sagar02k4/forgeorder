package com.sagar.forgeorder.refunds.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refunds")
@Getter
public class Refund {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "payment_attempt_id", nullable = false)
    private UUID paymentAttemptId;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RefundStatus status;

    @Column(name = "provider_refund_id")
    private String providerRefundId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Refund() {
    }

    public Refund(UUID orderId, UUID paymentAttemptId, String idempotencyKey, BigDecimal amount) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.paymentAttemptId = paymentAttemptId;
        this.idempotencyKey = idempotencyKey;
        this.amount = amount;
        this.status = RefundStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markSucceeded(String providerRefundId) {
        this.status = RefundStatus.SUCCEEDED;
        this.providerRefundId = providerRefundId;
        this.updatedAt = Instant.now();
    }

    public void markFailed() {
        this.status = RefundStatus.FAILED;
        this.updatedAt = Instant.now();
    }
}