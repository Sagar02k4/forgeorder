package com.sagar.forgeorder.outbox.persistence;

import com.sagar.forgeorder.outbox.domain.OutboxEvent;
import com.sagar.forgeorder.outbox.domain.OutboxEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, java.util.UUID> {
    List<OutboxEvent> findByStatus(OutboxEventStatus status);
}