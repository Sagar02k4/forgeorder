package com.sagar.forgeorder.common.messaging;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class ProcessedMessageId implements Serializable {

    private String consumerName;
    private UUID eventId;

    public ProcessedMessageId() {
    }

    public ProcessedMessageId(String consumerName, UUID eventId) {
        this.consumerName = consumerName;
        this.eventId = eventId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProcessedMessageId that)) return false;
        return Objects.equals(consumerName, that.consumerName) && Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(consumerName, eventId);
    }
}