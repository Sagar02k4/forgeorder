package com.sagar.forgeorder.orders.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
public class Order {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Column(name = "subtotal", nullable = false, precision = 19, scale = 4)
    private BigDecimal subtotal;

    @Column(name = "tax", nullable = false, precision = 19, scale = 4)
    private BigDecimal tax;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Order() {
        // required by JPA/Hibernate — do not use directly
    }

    public Order(UUID customerId, BigDecimal subtotal, BigDecimal tax) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.status = OrderStatus.DRAFT;
        this.subtotal = subtotal;
        this.tax = tax;
        this.totalAmount = subtotal.add(tax);
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * The only way to change an order's status.
     * Enforces the state machine — invalid transitions throw immediately.
     */
    public OrderAuditEvent transitionTo(OrderStatus newStatus,
                                        String actorType,
                                        String actorId,
                                        String correlationId,
                                        String causationId,
                                        String reason) {
        OrderStateMachine.validateTransition(this.status, newStatus);

        OrderStatus previousState = this.status;
        this.status = newStatus;
        this.updatedAt = Instant.now();

        return new OrderAuditEvent(
                this.id,
                previousState,
                newStatus,
                actorType,
                actorId,
                correlationId,
                causationId,
                reason
        );
    }
}