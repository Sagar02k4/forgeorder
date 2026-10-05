package com.sagar.forgeorder.refunds.application;

import com.sagar.forgeorder.orders.domain.Order;
import com.sagar.forgeorder.orders.domain.OrderAuditEvent;
import com.sagar.forgeorder.orders.domain.OrderNotFoundException;
import com.sagar.forgeorder.orders.domain.OrderStatus;
import com.sagar.forgeorder.orders.persistence.OrderAuditEventRepository;
import com.sagar.forgeorder.orders.persistence.OrderRepository;
import com.sagar.forgeorder.payments.domain.PaymentAttempt;
import com.sagar.forgeorder.payments.domain.PaymentGatewayResult;
import com.sagar.forgeorder.payments.gateway.PaymentGateway;
import com.sagar.forgeorder.payments.persistence.PaymentAttemptRepository;
import com.sagar.forgeorder.refunds.domain.InvalidRefundException;
import com.sagar.forgeorder.refunds.domain.Refund;
import com.sagar.forgeorder.refunds.persistence.RefundRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class RefundService {

    private final OrderRepository orderRepository;
    private final OrderAuditEventRepository auditEventRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final RefundRepository refundRepository;
    private final PaymentGateway paymentGateway;

    public RefundService(OrderRepository orderRepository,
                         OrderAuditEventRepository auditEventRepository,
                         PaymentAttemptRepository paymentAttemptRepository,
                         RefundRepository refundRepository,
                         PaymentGateway paymentGateway) {
        this.orderRepository = orderRepository;
        this.auditEventRepository = auditEventRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.refundRepository = refundRepository;
        this.paymentGateway = paymentGateway;
    }

    public void initiateRefund(UUID orderId, String idempotencyKey, BigDecimal requestedAmount, String correlationId) {
        Refund refund = beginRefund(orderId, idempotencyKey, requestedAmount, correlationId);

        PaymentAttempt paymentAttempt = paymentAttemptRepository.findById(refund.getPaymentAttemptId())
                .orElseThrow(() -> new IllegalStateException("PaymentAttempt not found"));

        PaymentGatewayResult result = paymentGateway.refund(
                idempotencyKey, paymentAttempt.getProviderPaymentId(), requestedAmount
        );

        applyRefundResult(refund.getId(), orderId, result, correlationId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Refund beginRefund(UUID orderId, String idempotencyKey, BigDecimal requestedAmount, String correlationId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() != OrderStatus.CONFIRMED && order.getStatus() != OrderStatus.FULFILLED) {
            throw new InvalidRefundException("Order is not in a refundable state: " + order.getStatus());
        }

        PaymentAttempt paymentAttempt = paymentAttemptRepository
                .findFirstByOrderIdOrderByCreatedAtDesc(orderId)
                .orElseThrow(() -> new InvalidRefundException("No payment found for order: " + orderId));

        BigDecimal alreadyRefunded = refundRepository.sumSucceededRefundsByOrderId(orderId);
        BigDecimal totalAfterThisRefund = alreadyRefunded.add(requestedAmount);

        if (totalAfterThisRefund.compareTo(paymentAttempt.getAmount()) > 0) {
            throw new InvalidRefundException(
                    "Refund amount exceeds captured payment. Already refunded: " + alreadyRefunded +
                            ", requested: " + requestedAmount + ", captured: " + paymentAttempt.getAmount()
            );
        }

        OrderAuditEvent pendingEvent = order.transitionTo(
                OrderStatus.REFUND_PENDING, "CUSTOMER", order.getCustomerId().toString(),
                correlationId, null, "Refund requested: " + requestedAmount
        );
        auditEventRepository.save(pendingEvent);
        orderRepository.save(order);

        Refund refund = new Refund(orderId, paymentAttempt.getId(), idempotencyKey, requestedAmount);
        refundRepository.save(refund);

        return refund;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyRefundResult(UUID refundId, UUID orderId, PaymentGatewayResult result, String correlationId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new IllegalStateException("Refund not found: " + refundId));

        if (result.outcome() == PaymentGatewayResult.GatewayOutcome.SUCCESS) {
            refund.markSucceeded(result.providerPaymentId());

            OrderAuditEvent refundedEvent = order.transitionTo(
                    OrderStatus.REFUNDED, "SYSTEM_WORKER", null,
                    correlationId, result.providerPaymentId(), "Refund succeeded"
            );
            auditEventRepository.save(refundedEvent);
        } else {
            refund.markFailed();
            // Order stays in REFUND_PENDING — requires manual/operations follow-up in a full implementation
        }

        refundRepository.save(refund);
        orderRepository.save(order);
    }
}