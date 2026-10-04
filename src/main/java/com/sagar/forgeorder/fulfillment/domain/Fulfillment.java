package com.sagar.forgeorder.fulfillment.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fulfillments")
@Getter
public class Fulfillment {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "shipment_reference")
    private String shipmentReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private FulfillmentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Fulfillment() {
    }

    public Fulfillment(UUID orderId) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.status = FulfillmentStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markShipped(String shipmentReference) {
        this.status = FulfillmentStatus.SHIPPED;
        this.shipmentReference = shipmentReference;
        this.updatedAt = Instant.now();
    }
}