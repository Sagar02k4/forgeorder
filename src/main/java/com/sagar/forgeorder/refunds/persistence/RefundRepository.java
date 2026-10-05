package com.sagar.forgeorder.refunds.persistence;

import com.sagar.forgeorder.refunds.domain.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM Refund r WHERE r.orderId = :orderId AND r.status = 'SUCCEEDED'")
    BigDecimal sumSucceededRefundsByOrderId(@Param("orderId") UUID orderId);
}