package com.sagar.forgeorder.orders.application;

import com.sagar.forgeorder.orders.domain.Order;
import com.sagar.forgeorder.orders.domain.OrderAuditEvent;
import com.sagar.forgeorder.orders.domain.OrderStatus;
import com.sagar.forgeorder.orders.persistence.OrderAuditEventRepository;
import com.sagar.forgeorder.orders.persistence.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
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

    @Test
    void createOrderPersistsOrderAndAuditEventTogether() {
        UUID customerId = UUID.randomUUID();
        BigDecimal subtotal = new BigDecimal("100.00");
        BigDecimal tax = new BigDecimal("18.00");
        String correlationId = "test-corr-" + UUID.randomUUID();

        Order createdOrder = orderService.createOrder(customerId, subtotal, tax, correlationId);

        Optional<Order> fetchedOrder = orderRepository.findById(createdOrder.getId());
        assertThat(fetchedOrder).isPresent();
        assertThat(fetchedOrder.get().getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(fetchedOrder.get().getCustomerId()).isEqualTo(customerId);

        List<OrderAuditEvent> auditEvents = auditEventRepository.findByOrderIdOrderByOccurredAtAsc(createdOrder.getId());
        assertThat(auditEvents).hasSize(1);
        assertThat(auditEvents.get(0).getPreviousState()).isEqualTo(OrderStatus.DRAFT);
        assertThat(auditEvents.get(0).getNewState()).isEqualTo(OrderStatus.CREATED);
        assertThat(auditEvents.get(0).getCorrelationId()).isEqualTo(correlationId);
    }
}