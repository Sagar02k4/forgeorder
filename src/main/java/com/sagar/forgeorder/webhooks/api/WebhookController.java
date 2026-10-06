package com.sagar.forgeorder.webhooks.api;

import com.sagar.forgeorder.webhooks.application.WebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/mock-payment")
    public ResponseEntity<Void> receiveWebhook(
            @RequestBody String payload,
            @RequestHeader("X-Webhook-Signature") String signature) {

        webhookService.processWebhook(payload, signature);
        return ResponseEntity.ok().build();
    }
}