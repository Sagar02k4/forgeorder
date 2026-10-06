package com.sagar.forgeorder.webhooks.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Entity
@Table(name = "payment_webhook_events")
@Getter
public class WebhookEvent {

    @Id
    @Column(name = "provider_event_id")
    private String providerEventId;

    @Column(name = "provider", nullable = false)
    private String provider;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "provider_payment_id", nullable = false)
    private String providerPaymentId;

    @Column(name = "event_created_at", nullable = false)
    private Instant eventCreatedAt;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "signature", nullable = false)
    private String signature;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected WebhookEvent() {
    }

    public WebhookEvent(String providerEventId, String provider, String eventType,
                        String providerPaymentId, Instant eventCreatedAt, String payload, String signature) {
        this.providerEventId = providerEventId;
        this.provider = provider;
        this.eventType = eventType;
        this.providerPaymentId = providerPaymentId;
        this.eventCreatedAt = eventCreatedAt;
        this.payload = payload;
        this.signature = signature;
        this.processedAt = Instant.now();
    }
}