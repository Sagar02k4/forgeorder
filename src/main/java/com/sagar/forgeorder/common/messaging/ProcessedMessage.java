package com.sagar.forgeorder.common.messaging;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_messages")
@Getter
@IdClass(ProcessedMessageId.class)
public class ProcessedMessage {

    @Id
    @Column(name = "consumer_name")
    private String consumerName;

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedMessage() {
        // required by JPA
    }

    public ProcessedMessage(String consumerName, UUID eventId) {
        this.consumerName = consumerName;
        this.eventId = eventId;
        this.processedAt = Instant.now();
    }
}