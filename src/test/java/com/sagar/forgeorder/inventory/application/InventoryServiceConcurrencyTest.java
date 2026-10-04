package com.sagar.forgeorder.inventory.application;

import com.sagar.forgeorder.inventory.domain.Inventory;
import com.sagar.forgeorder.inventory.persistence.InventoryRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class InventoryServiceConcurrencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void onlyOneConcurrentBuyerReservesTheLastUnit() throws InterruptedException, ExecutionException, TimeoutException {
        UUID productId = UUID.randomUUID();
        inventoryRepository.save(new Inventory(productId, 1)); // sirf 1 item stock mein hai

        Order dummyOrder = new Order(
                UUID.randomUUID(), productId, 1,
                new BigDecimal("100.00"), new BigDecimal("18.00")
        );
        orderRepository.save(dummyOrder);
        UUID orderId = dummyOrder.getId();

        int numberOfThreads = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < numberOfThreads; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    boolean reserved = inventoryService.reserveStock(orderId, productId, 1);
                    if (reserved) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        readyLatch.await();
        startLatch.countDown();

        for (Future<?> future : futures) {
            future.get(15, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(numberOfThreads - 1);

        Inventory finalState = inventoryRepository.findById(productId).orElseThrow();
        assertThat(finalState.getAvailableQuantity()).isEqualTo(0);
        assertThat(finalState.getReservedQuantity()).isEqualTo(1);
    }
}