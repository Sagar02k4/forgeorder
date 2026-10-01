package com.sagar.forgeorder.orders.application;

import com.sagar.forgeorder.inventory.domain.Inventory;
import com.sagar.forgeorder.inventory.persistence.InventoryRepository;
import com.sagar.forgeorder.orders.domain.Order;
import com.sagar.forgeorder.orders.domain.OrderAuditEvent;
import com.sagar.forgeorder.orders.domain.OrderStatus;
import com.sagar.forgeorder.orders.persistence.OrderAuditEventRepository;
import com.sagar.forgeorder.orders.persistence.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class OrderServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderAuditEventRepository auditEventRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Test
    void createOrderPersistsOrderAndAuditEventTogetherWhenStockAvailable() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        inventoryRepository.save(new Inventory(productId, 10)); // kaafi stock hai

        BigDecimal subtotal = new BigDecimal("100.00");
        BigDecimal tax = new BigDecimal("18.00");
        String correlationId = "test-corr-" + UUID.randomUUID();

        Order createdOrder = orderService.createOrder(
                customerId, productId, 1, subtotal, tax, correlationId
        );

        Optional<Order> fetchedOrder = orderRepository.findById(createdOrder.getId());
        assertThat(fetchedOrder).isPresent();
        assertThat(fetchedOrder.get().getStatus()).isEqualTo(OrderStatus.INVENTORY_RESERVED);

        List<OrderAuditEvent> auditEvents =
                auditEventRepository.findByOrderIdOrderByOccurredAtAsc(createdOrder.getId());
        assertThat(auditEvents).hasSize(3); // CREATED, RESERVATION_PENDING, RESERVED

        Inventory inventoryAfter = inventoryRepository.findById(productId).orElseThrow();
        assertThat(inventoryAfter.getAvailableQuantity()).isEqualTo(9);
        assertThat(inventoryAfter.getReservedQuantity()).isEqualTo(1);
    }

    @Test
    void createOrderCancelsOrderWhenStockUnavailable() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        inventoryRepository.save(new Inventory(productId, 0)); // koi stock nahi hai

        BigDecimal subtotal = new BigDecimal("100.00");
        BigDecimal tax = new BigDecimal("18.00");
        String correlationId = "test-corr-" + UUID.randomUUID();

        Order createdOrder = orderService.createOrder(
                customerId, productId, 1, subtotal, tax, correlationId
        );

        Optional<Order> fetchedOrder = orderRepository.findById(createdOrder.getId());
        assertThat(fetchedOrder).isPresent();
        assertThat(fetchedOrder.get().getStatus()).isEqualTo(OrderStatus.CANCELLED);

        List<OrderAuditEvent> auditEvents =
                auditEventRepository.findByOrderIdOrderByOccurredAtAsc(createdOrder.getId());
        assertThat(auditEvents).hasSize(4); // CREATED, PENDING, UNAVAILABLE, CANCELLED
    }
}