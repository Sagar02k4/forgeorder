package com.sagar.forgeorder.inventory.application;

import com.sagar.forgeorder.inventory.domain.InventoryReservation;
import com.sagar.forgeorder.inventory.domain.ReservationStatus;
import com.sagar.forgeorder.inventory.persistence.InventoryReservationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class ReservationExpiryScheduler {

    private final InventoryReservationRepository reservationRepository;
    private final InventoryService inventoryService;

    public ReservationExpiryScheduler(InventoryReservationRepository reservationRepository,
                                      InventoryService inventoryService) {
        this.reservationRepository = reservationRepository;
        this.inventoryService = inventoryService;
    }

    @Scheduled(fixedDelay = 60000)
    public void releaseExpiredReservations() {
        List<InventoryReservation> expired = reservationRepository
                .findByStatusAndExpiresAtBefore(ReservationStatus.ACTIVE, Instant.now());

        for (InventoryReservation reservation : expired) {
            inventoryService.releaseReservation(reservation.getId());
        }
    }
}