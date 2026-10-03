package com.sagar.forgeorder.outbox.application;

import com.sagar.forgeorder.outbox.domain.OutboxEvent;
import com.sagar.forgeorder.outbox.persistence.OutboxEventRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

@Service
public class OutboxEventWriter {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventWriter(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    public void writeOrderEvent(UUID orderId, String eventType) {
        Map<String, Object> payloadMap = Map.of(
                "orderId", orderId.toString(),
                "eventType", eventType
        );
        String payload = serializeToJson(payloadMap);

        OutboxEvent event = new OutboxEvent("ORDER", orderId, eventType, payload);
        outboxEventRepository.save(event);
    }

    private String serializeToJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }
    }
}