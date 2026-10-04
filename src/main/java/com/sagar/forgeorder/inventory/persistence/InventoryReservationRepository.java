package com.sagar.forgeorder.inventory.persistence;

import com.sagar.forgeorder.inventory.domain.InventoryReservation;
import com.sagar.forgeorder.inventory.domain.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, UUID> {

    List<InventoryReservation> findByStatusAndExpiresAtBefore(ReservationStatus status, Instant now);

    Optional<InventoryReservation> findByOrderIdAndStatus(UUID orderId, ReservationStatus status);
}