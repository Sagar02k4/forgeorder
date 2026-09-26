package com.sagar.forgeorder.orders.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_audit_events")
@Getter
public class OrderAuditEvent {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_state")
    private OrderStatus previousState; // nullable — first event (DRAFT creation) has no previous state

    @Enumerated(EnumType.STRING)
    @Column(name = "new_state", nullable = false)
    private OrderStatus newState;

    @Column(name = "actor_type", nullable = false)
    private String actorType; // e.g. "CUSTOMER", "SYSTEM_WORKER", "OPERATIONS_ADMIN"

    @Column(name = "actor_id")
    private String actorId; // nullable — e.g. system worker might not have a user id

    @Column(name = "correlation_id", nullable = false)
    private String correlationId;

    @Column(name = "causation_id")
    private String causationId; // nullable — e.g. webhook event ID that triggered this

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected OrderAuditEvent() {
        // required by JPA
    }

    public OrderAuditEvent(UUID orderId,
                           OrderStatus previousState,
                           OrderStatus newState,
                           String actorType,
                           String actorId,
                           String correlationId,
                           String causationId,
                           String reason) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.previousState = previousState;
        this.newState = newState;
        this.actorType = actorType;
        this.actorId = actorId;
        this.correlationId = correlationId;
        this.causationId = causationId;
        this.reason = reason;
        this.occurredAt = Instant.now();
    }
}