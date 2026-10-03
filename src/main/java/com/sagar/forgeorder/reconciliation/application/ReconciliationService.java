package com.sagar.forgeorder.reconciliation.application;

import com.sagar.forgeorder.orders.domain.Order;
import com.sagar.forgeorder.orders.domain.OrderAuditEvent;
import com.sagar.forgeorder.orders.domain.OrderStatus;
import com.sagar.forgeorder.orders.persistence.OrderAuditEventRepository;
import com.sagar.forgeorder.orders.persistence.OrderRepository;
import com.sagar.forgeorder.payments.domain.PaymentAttempt;
import com.sagar.forgeorder.payments.domain.PaymentGatewayResult;
import com.sagar.forgeorder.payments.gateway.PaymentGateway;
import com.sagar.forgeorder.payments.persistence.PaymentAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ReconciliationService {

    private final OrderRepository orderRepository;
    private final OrderAuditEventRepository auditEventRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentGateway paymentGateway;

    public ReconciliationService(OrderRepository orderRepository,
                                 OrderAuditEventRepository auditEventRepository,
                                 PaymentAttemptRepository paymentAttemptRepository,
                                 PaymentGateway paymentGateway) {
        this.orderRepository = orderRepository;
        this.auditEventRepository = auditEventRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.paymentGateway = paymentGateway;
    }

    @Transactional
    public void reconcileOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.RECONCILIATION_REQUIRED) {
            return; // Nothing to do — already resolved, or never needed reconciliation.
        }

        PaymentAttempt attempt = paymentAttemptRepository
                .findFirstByOrderIdOrderByCreatedAtDesc(orderId)
                .orElseThrow(() -> new IllegalStateException(
                        "No payment attempt found for order under reconciliation: " + orderId));

        PaymentGatewayResult result = paymentGateway.queryStatus(attempt.getIdempotencyKey());

        switch (result.outcome()) {
            case SUCCESS -> {
                attempt.markSucceeded(result.providerPaymentId(), result.rawResponse());

                OrderAuditEvent succeededEvent = order.transitionTo(
                        OrderStatus.PAYMENT_SUCCEEDED, "SYSTEM_WORKER", null,
                        "reconciliation", result.providerPaymentId(),
                        "Reconciliation confirmed payment succeeded"
                );
                auditEventRepository.save(succeededEvent);

                OrderAuditEvent confirmedEvent = order.transitionTo(
                        OrderStatus.CONFIRMED, "SYSTEM_WORKER", null,
                        "reconciliation", result.providerPaymentId(),
                        "Order confirmed after reconciliation"
                );
                auditEventRepository.save(confirmedEvent);
            }
            case DECLINED -> {
                attempt.markFailed(result.declineReason(), result.rawResponse());

                OrderAuditEvent failedEvent = order.transitionTo(
                        OrderStatus.PAYMENT_FAILED, "SYSTEM_WORKER", null,
                        "reconciliation", null,
                        "Reconciliation confirmed payment failed: " + result.declineReason()
                );
                auditEventRepository.save(failedEvent);
            }
            case NOT_FOUND, TIMEOUT -> {
                // Still unresolved — leave the order in RECONCILIATION_REQUIRED.
                // A real system would track attempt count/age here and escalate
                // to an operations task after exceeding a policy limit.
            }
        }

        paymentAttemptRepository.save(attempt);
        orderRepository.save(order);
    }
}