package com.sagar.forgeorder.payments.application;

import com.sagar.forgeorder.inventory.domain.Inventory;
import com.sagar.forgeorder.inventory.persistence.InventoryRepository;
import com.sagar.forgeorder.orders.application.OrderService;
import com.sagar.forgeorder.orders.domain.Order;
import com.sagar.forgeorder.orders.domain.OrderStatus;
import com.sagar.forgeorder.orders.persistence.OrderRepository;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class PaymentServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentAttemptRepository paymentAttemptRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    private UUID createReservedOrder() {
        UUID productId = UUID.randomUUID();
        inventoryRepository.save(new Inventory(productId, 10));

        Order order = orderService.createOrder(
                UUID.randomUUID(), productId, 1,
                new BigDecimal("100.00"), new BigDecimal("18.00"),
                "test-corr-" + UUID.randomUUID()
        );
        return order.getId();
    }

    @Test
    void successfulPaymentConfirmsOrder() {
        UUID orderId = createReservedOrder();

        paymentService.initiatePayment(orderId, "pay-idem-" + UUID.randomUUID(), "tok_success_visa", "corr-1");

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        List<PaymentAttempt> attempts = paymentAttemptRepository.findAll().stream()
                .filter(a -> a.getOrderId().equals(orderId)).toList();
        assertThat(attempts).hasSize(1);
        assertThat(attempts.get(0).getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(attempts.get(0).getProviderPaymentId()).isNotNull();
    }

    @Test
    void declinedPaymentMarksOrderAsFailed() {
        UUID orderId = createReservedOrder();

        paymentService.initiatePayment(orderId, "pay-idem-" + UUID.randomUUID(), "tok_decline_visa", "corr-2");

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
    }

    @Test
    void timeoutPaymentFlagsOrderForReconciliation() {
        UUID orderId = createReservedOrder();

        paymentService.initiatePayment(orderId, "pay-idem-" + UUID.randomUUID(), "tok_timeout_visa", "corr-3");

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.RECONCILIATION_REQUIRED);

        List<PaymentAttempt> attempts = paymentAttemptRepository.findAll().stream()
                .filter(a -> a.getOrderId().equals(orderId)).toList();
        assertThat(attempts.get(0).getStatus()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(attempts.get(0).getProviderPaymentId()).isNull();
    }
}