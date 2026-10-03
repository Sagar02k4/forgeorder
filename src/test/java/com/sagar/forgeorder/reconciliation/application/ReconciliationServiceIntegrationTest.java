package com.sagar.forgeorder.reconciliation.application;

import com.sagar.forgeorder.inventory.domain.Inventory;
import com.sagar.forgeorder.inventory.persistence.InventoryRepository;
import com.sagar.forgeorder.orders.application.OrderService;
import com.sagar.forgeorder.orders.domain.Order;
import com.sagar.forgeorder.orders.domain.OrderStatus;
import com.sagar.forgeorder.orders.persistence.OrderRepository;
import com.sagar.forgeorder.payments.application.PaymentService;
import com.sagar.forgeorder.payments.domain.PaymentAttempt;
import com.sagar.forgeorder.payments.domain.PaymentStatus;
import com.sagar.forgeorder.payments.persistence.PaymentAttemptRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class ReconciliationServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentAttemptRepository paymentAttemptRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Test
    void reconciliationResolvesTimedOutPaymentToConfirmed() {
        UUID productId = UUID.randomUUID();
        inventoryRepository.save(new Inventory(productId, 10));

        Order order = orderService.createOrder(
                UUID.randomUUID(), productId, 1,
                new BigDecimal("100.00"), new BigDecimal("18.00"),
                "test-corr-" + UUID.randomUUID()
        );

        String idempotencyKey = "pay-idem-" + UUID.randomUUID();
        paymentService.initiatePayment(order.getId(), idempotencyKey, "tok_timeout_visa", "corr-1");

        Order orderAfterTimeout = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(orderAfterTimeout.getStatus()).isEqualTo(OrderStatus.RECONCILIATION_REQUIRED);

        reconciliationService.reconcileOrder(order.getId());

        Order orderAfterReconciliation = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(orderAfterReconciliation.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        PaymentAttempt attempt = paymentAttemptRepository
                .findFirstByOrderIdOrderByCreatedAtDesc(order.getId())
                .orElseThrow();
        assertThat(attempt.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(attempt.getProviderPaymentId()).isNotNull();
    }

    @Test
    void reconcilingAnOrderNotInReconciliationStateIsANoOp() {
        UUID productId = UUID.randomUUID();
        inventoryRepository.save(new Inventory(productId, 10));

        Order order = orderService.createOrder(
                UUID.randomUUID(), productId, 1,
                new BigDecimal("100.00"), new BigDecimal("18.00"),
                "test-corr-" + UUID.randomUUID()
        );
        // Order is INVENTORY_RESERVED here — never went through payment/reconciliation.

        reconciliationService.reconcileOrder(order.getId());

        Order unchangedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(unchangedOrder.getStatus()).isEqualTo(OrderStatus.INVENTORY_RESERVED);
    }
}