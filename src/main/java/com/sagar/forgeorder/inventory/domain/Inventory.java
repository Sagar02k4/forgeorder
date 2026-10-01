package com.sagar.forgeorder.inventory.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory")
@Getter
public class Inventory {

    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Version
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Inventory() {
        // required by JPA
    }

    public Inventory(UUID productId, int initialQuantity) {
        this.productId = productId;
        this.availableQuantity = initialQuantity;
        this.reservedQuantity = 0;
        this.updatedAt = Instant.now();
    }
}