package com.sagar.forgeorder.orders.persistence;

import com.sagar.forgeorder.orders.domain.OrderAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderAuditEventRepository extends JpaRepository<OrderAuditEvent, UUID> {

    List<OrderAuditEvent> findByOrderIdOrderByOccurredAtAsc(UUID orderId);
}