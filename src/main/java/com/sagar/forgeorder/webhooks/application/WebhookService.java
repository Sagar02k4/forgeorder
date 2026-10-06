package com.sagar.forgeorder.webhooks.application;

import com.sagar.forgeorder.orders.domain.Order;
import com.sagar.forgeorder.orders.domain.OrderAuditEvent;
import com.sagar.forgeorder.orders.domain.OrderStateMachine;
import com.sagar.forgeorder.orders.domain.OrderStatus;
import com.sagar.forgeorder.orders.persistence.OrderAuditEventRepository;
import com.sagar.forgeorder.orders.persistence.OrderRepository;
import com.sagar.forgeorder.payments.domain.PaymentAttempt;
import com.sagar.forgeorder.payments.persistence.PaymentAttemptRepository;
import com.sagar.forgeorder.webhooks.domain.InvalidWebhookSignatureException;
import com.sagar.forgeorder.webhooks.domain.WebhookEvent;
import com.sagar.forgeorder.webhooks.persistence.WebhookEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

@Service
public class WebhookService {

    private final WebhookEventRepository webhookEventRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final OrderRepository orderRepository;
    private final OrderAuditEventRepository auditEventRepository;
    private final WebhookSignatureVerifier signatureVerifier;
    private final ObjectMapper objectMapper;

    public WebhookService(WebhookEventRepository webhookEventRepository,
                          PaymentAttemptRepository paymentAttemptRepository,
                          OrderRepository orderRepository,
                          OrderAuditEventRepository auditEventRepository,
                          WebhookSignatureVerifier signatureVerifier,
                          ObjectMapper objectMapper) {
        this.webhookEventRepository = webhookEventRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.orderRepository = orderRepository;
        this.auditEventRepository = auditEventRepository;
        this.signatureVerifier = signatureVerifier;
        this.objectMapper = objectMapper;
    }

    public void processWebhook(String payload, String signature) {
        if (!signatureVerifier.verifySignature(payload, signature)) {
            throw new InvalidWebhookSignatureException("Webhook signature verification failed");
        }

        JsonNode json = objectMapper.readTree(payload);
        String providerEventId = json.get("providerEventId").asString();
        String eventType = json.get("eventType").asString();
        String providerPaymentId = json.get("providerPaymentId").asString();
        Instant eventCreatedAt = Instant.parse(json.get("eventCreatedAt").asString());

        boolean isNewEvent = tryRecordWebhookEvent(
                providerEventId, eventType, providerPaymentId, eventCreatedAt, payload, signature
        );

        if (!isNewEvent) {
            return; // Duplicate — already processed, acknowledge safely without repeating effect
        }

        applyWebhookEffect(providerPaymentId, eventType);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected boolean tryRecordWebhookEvent(String providerEventId, String eventType, String providerPaymentId,
                                            Instant eventCreatedAt, String payload, String signature) {
        try {
            WebhookEvent event = new WebhookEvent(
                    providerEventId, "MOCK_PROVIDER", eventType, providerPaymentId,
                    eventCreatedAt, payload, signature
            );
            webhookEventRepository.save(event);
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    @Transactional
    protected void applyWebhookEffect(String providerPaymentId, String eventType) {
        PaymentAttempt attempt = paymentAttemptRepository.findByProviderPaymentId(providerPaymentId)
                .orElse(null);

        if (attempt == null) {
            return; // Unknown payment reference — nothing to apply
        }

        Order order = orderRepository.findById(attempt.getOrderId())
                .orElseThrow(() -> new IllegalStateException("Order not found for payment attempt"));

        if ("PAYMENT_SUCCEEDED".equals(eventType)
                && OrderStateMachine.canTransition(order.getStatus(), OrderStatus.PAYMENT_SUCCEEDED)) {

            attempt.markSucceeded(providerPaymentId, "webhook confirmed");

            OrderAuditEvent succeededEvent = order.transitionTo(
                    OrderStatus.PAYMENT_SUCCEEDED, "SYSTEM_WORKER", null,
                    "webhook", providerPaymentId, "Payment succeeded (confirmed via webhook)"
            );
            auditEventRepository.save(succeededEvent);

            OrderAuditEvent confirmedEvent = order.transitionTo(
                    OrderStatus.CONFIRMED, "SYSTEM_WORKER", null,
                    "webhook", providerPaymentId, "Order confirmed via webhook"
            );
            auditEventRepository.save(confirmedEvent);

            paymentAttemptRepository.save(attempt);
            orderRepository.save(order);
        }
    }
}