package com.sagar.forgeorder.payments.application;

import com.sagar.forgeorder.orders.domain.Order;
import com.sagar.forgeorder.orders.domain.OrderAuditEvent;
import com.sagar.forgeorder.orders.domain.OrderNotFoundException;
import com.sagar.forgeorder.orders.domain.OrderStatus;
import com.sagar.forgeorder.orders.persistence.OrderAuditEventRepository;
import com.sagar.forgeorder.orders.persistence.OrderRepository;
import com.sagar.forgeorder.outbox.application.OutboxEventWriter;
import com.sagar.forgeorder.payments.domain.PaymentAttempt;
import com.sagar.forgeorder.payments.domain.PaymentGatewayResult;
import com.sagar.forgeorder.payments.gateway.PaymentGateway;
import com.sagar.forgeorder.payments.persistence.PaymentAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class PaymentService {

    private final OrderRepository orderRepository;
    private final OrderAuditEventRepository auditEventRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentGateway paymentGateway;
    private final OutboxEventWriter outboxEventWriter;

    public PaymentService(OrderRepository orderRepository,
                          OrderAuditEventRepository auditEventRepository,
                          PaymentAttemptRepository paymentAttemptRepository,
                          PaymentGateway paymentGateway, OutboxEventWriter outboxEventWriter) {
        this.orderRepository = orderRepository;
        this.auditEventRepository = auditEventRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.paymentGateway = paymentGateway;
        this.outboxEventWriter = outboxEventWriter;
    }

    public void initiatePayment(UUID orderId, String idempotencyKey, String cardToken, String correlationId) {
        PaymentAttempt paymentAttempt = beginPaymentAttempt(orderId, idempotencyKey, correlationId);

        PaymentGatewayResult result = paymentGateway.charge(
                idempotencyKey, paymentAttempt.getAmount(), cardToken
        );

        applyGatewayResult(orderId, paymentAttempt.getId(), result, correlationId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentAttempt beginPaymentAttempt(UUID orderId, String idempotencyKey, String correlationId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        OrderAuditEvent pendingEvent = order.transitionTo(
                OrderStatus.PAYMENT_PENDING, "CUSTOMER", order.getCustomerId().toString(),
                correlationId, null, "Payment initiation requested"
        );
        auditEventRepository.save(pendingEvent);
        orderRepository.save(order);

        PaymentAttempt attempt = new PaymentAttempt(orderId, "MOCK_PROVIDER", idempotencyKey, order.getTotalAmount());
        paymentAttemptRepository.save(attempt);

        return attempt;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyGatewayResult(UUID orderId, UUID paymentAttemptId,
                                   PaymentGatewayResult result, String correlationId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        PaymentAttempt attempt = paymentAttemptRepository.findById(paymentAttemptId)
                .orElseThrow(() -> new IllegalStateException("PaymentAttempt not found: " + paymentAttemptId));

        switch (result.outcome()) {
            case SUCCESS -> {
                attempt.markSucceeded(result.providerPaymentId(), result.rawResponse());

                OrderAuditEvent succeededEvent = order.transitionTo(
                        OrderStatus.PAYMENT_SUCCEEDED, "SYSTEM_WORKER", null,
                        correlationId, result.providerPaymentId(), "Payment succeeded"
                );
                auditEventRepository.save(succeededEvent);

                OrderAuditEvent confirmedEvent = order.transitionTo(
                        OrderStatus.CONFIRMED, "SYSTEM_WORKER", null,
                        correlationId, result.providerPaymentId(), "Order confirmed after payment"
                );
                auditEventRepository.save(confirmedEvent);

                outboxEventWriter.writeOrderEvent(order.getId(), "ORDER_CONFIRMED");
            }
            case DECLINED -> {
                attempt.markFailed(result.declineReason(), result.rawResponse());

                OrderAuditEvent failedEvent = order.transitionTo(
                        OrderStatus.PAYMENT_FAILED, "SYSTEM_WORKER", null,
                        correlationId, null, "Payment declined: " + result.declineReason()
                );
                auditEventRepository.save(failedEvent);
            }
            case TIMEOUT -> {
                attempt.markUnknown(result.rawResponse());

                OrderAuditEvent unknownEvent = order.transitionTo(
                        OrderStatus.PAYMENT_STATUS_UNKNOWN, "SYSTEM_WORKER", null,
                        correlationId, null, "Payment gateway timed out — status unknown"
                );
                auditEventRepository.save(unknownEvent);

                OrderAuditEvent reconEvent = order.transitionTo(
                        OrderStatus.RECONCILIATION_REQUIRED, "SYSTEM_WORKER", null,
                        correlationId, null, "Flagged for reconciliation due to payment timeout"
                );
                auditEventRepository.save(reconEvent);
            }
        }

        paymentAttemptRepository.save(attempt);
        orderRepository.save(order);
    }
}