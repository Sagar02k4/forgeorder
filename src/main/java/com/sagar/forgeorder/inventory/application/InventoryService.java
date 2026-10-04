package com.sagar.forgeorder.inventory.application;

import com.sagar.forgeorder.inventory.domain.InventoryReservation;
import com.sagar.forgeorder.inventory.domain.ReservationStatus;
import com.sagar.forgeorder.inventory.persistence.InventoryRepository;
import com.sagar.forgeorder.inventory.persistence.InventoryReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;

    public InventoryService(InventoryRepository inventoryRepository,
                            InventoryReservationRepository reservationRepository) {
        this.inventoryRepository = inventoryRepository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional
    public boolean reserveStock(UUID orderId, UUID productId, int quantity) {
        int rowsUpdated = inventoryRepository.tryReserveStock(productId, quantity, Instant.now());

        if (rowsUpdated != 1) {
            return false;
        }

        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);
        InventoryReservation reservation = new InventoryReservation(orderId, productId, quantity, expiresAt);
        reservationRepository.save(reservation);

        return true;
    }

    @Transactional
    public void releaseReservation(UUID reservationId) {
        InventoryReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalStateException("Reservation not found: " + reservationId));

        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            return; // already released/expired — nothing to do
        }

        inventoryRepository.releaseStock(reservation.getProductId(), reservation.getQuantity(), Instant.now());
        reservation.markExpired();
        reservationRepository.save(reservation);
    }
}