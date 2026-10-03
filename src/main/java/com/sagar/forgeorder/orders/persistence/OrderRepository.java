package com.sagar.forgeorder.orders.persistence;

import com.sagar.forgeorder.orders.domain.Order;
import com.sagar.forgeorder.orders.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    @Modifying
    @Query("""
        UPDATE Order o
        SET o.reconciliationLeasedBy = :workerId,
            o.reconciliationLeasedUntil = :leaseUntil
        WHERE o.status = 'RECONCILIATION_REQUIRED'
          AND o.id = :orderId
          AND (o.reconciliationLeasedUntil IS NULL OR o.reconciliationLeasedUntil < :now)
        """)
    int tryAcquireLease(@Param("orderId") UUID orderId,
                        @Param("workerId") String workerId,
                        @Param("now") Instant now,
                        @Param("leaseUntil") Instant leaseUntil);

    List<Order> findByStatus(OrderStatus status);
}