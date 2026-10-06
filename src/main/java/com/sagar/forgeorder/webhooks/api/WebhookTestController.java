package com.sagar.forgeorder.webhooks.api;

import com.sagar.forgeorder.payments.domain.PaymentGatewayResult;
import com.sagar.forgeorder.payments.gateway.MockPaymentGateway;
import com.sagar.forgeorder.webhooks.application.WebhookSignatureVerifier;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/test/webhooks")
public class WebhookTestController {

    private final MockPaymentGateway mockPaymentGateway;
    private final WebhookSignatureVerifier signatureVerifier;

    public WebhookTestController(MockPaymentGateway mockPaymentGateway, WebhookSignatureVerifier signatureVerifier) {
        this.mockPaymentGateway = mockPaymentGateway;
        this.signatureVerifier = signatureVerifier;
    }

    public record SimulateWebhookRequest(String providerPaymentId, String eventType) {}
    public record SimulatedWebhookResponse(String payload, String signature) {}
    public record GatewayStatusResponse(String outcome, String providerPaymentId) {}

    @PostMapping("/simulate")
    public SimulatedWebhookResponse simulateWebhook(@RequestBody SimulateWebhookRequest request) {
        String payload = mockPaymentGateway.generateWebhookPayload(request.providerPaymentId(), request.eventType());
        String signature = signatureVerifier.computeSignature(payload);
        return new SimulatedWebhookResponse(payload, signature);
    }

    @GetMapping("/gateway-status")
    public GatewayStatusResponse getGatewayStatus(@RequestParam String idempotencyKey) {
        PaymentGatewayResult result = mockPaymentGateway.queryStatus(idempotencyKey);
        return new GatewayStatusResponse(result.outcome().name(), result.providerPaymentId());
    }
}