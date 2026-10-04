package com.sagar.forgeorder.inventory.persistence;

import com.sagar.forgeorder.inventory.domain.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    @Modifying
    @Query("""
            UPDATE Inventory i
            SET i.availableQuantity = i.availableQuantity - :quantity,
                i.reservedQuantity = i.reservedQuantity + :quantity,
                i.updatedAt = :now
            WHERE i.productId = :productId
              AND i.availableQuantity >= :quantity
            """)
    int tryReserveStock(@Param("productId") UUID productId,
                        @Param("quantity") int quantity,
                        @Param("now") Instant now);

    @Modifying
    @Query("""
        UPDATE Inventory i
        SET i.availableQuantity = i.availableQuantity + :quantity,
            i.reservedQuantity = i.reservedQuantity - :quantity,
            i.updatedAt = :now
        WHERE i.productId = :productId
        """)
    int releaseStock(@Param("productId") UUID productId,
                     @Param("quantity") int quantity,
                     @Param("now") Instant now);
}