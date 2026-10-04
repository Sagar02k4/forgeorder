package com.sagar.forgeorder.inventory.application;

import com.sagar.forgeorder.inventory.domain.Inventory;
import com.sagar.forgeorder.inventory.domain.InventoryReservation;
import com.sagar.forgeorder.inventory.domain.ReservationStatus;
import com.sagar.forgeorder.inventory.persistence.InventoryRepository;
import com.sagar.forgeorder.inventory.persistence.InventoryReservationRepository;
import com.sagar.forgeorder.orders.domain.Order;
import com.sagar.forgeorder.orders.persistence.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class ReservationExpiryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryReservationRepository reservationRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private ReservationExpiryScheduler reservationExpiryScheduler;

    @Test
    void expiredReservationReleasesStockAndMarksReservationExpired() {
        UUID productId = UUID.randomUUID();
        inventoryRepository.save(new Inventory(productId, 10));

        Order dummyOrder = new Order(
                UUID.randomUUID(), productId, 1,
                new BigDecimal("100.00"), new BigDecimal("18.00")
        );
        orderRepository.save(dummyOrder);

        // Actually reserve stock through the real service, so Inventory counts are correct
        boolean reserved = inventoryService.reserveStock(dummyOrder.getId(), productId, 1);
        assertThat(reserved).isTrue();

        // Fetch the reservation that was just created, and force its expiry into the past
        InventoryReservation activeReservation = reservationRepository
                .findByOrderIdAndStatus(dummyOrder.getId(), ReservationStatus.ACTIVE)
                .orElseThrow();

        // Directly manipulate expiry via a fresh reservation row simulating "already expired"
        reservationRepository.delete(activeReservation);
        InventoryReservation expiredReservation = new InventoryReservation(
                dummyOrder.getId(), productId, 1, Instant.now().minus(1, ChronoUnit.HOURS)
        );
        reservationRepository.save(expiredReservation);

        reservationExpiryScheduler.releaseExpiredReservations();

        InventoryReservation updatedReservation = reservationRepository.findById(expiredReservation.getId())
                .orElseThrow();
        assertThat(updatedReservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);

        Inventory updatedInventory = inventoryRepository.findById(productId).orElseThrow();
        assertThat(updatedInventory.getAvailableQuantity()).isEqualTo(10);
        assertThat(updatedInventory.getReservedQuantity()).isEqualTo(0);
    }
}