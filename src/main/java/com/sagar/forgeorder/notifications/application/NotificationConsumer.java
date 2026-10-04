package com.sagar.forgeorder.notifications.application;

import com.sagar.forgeorder.common.messaging.ProcessedMessageTracker;
import com.sagar.forgeorder.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Component
public class NotificationConsumer {

    private static final String CONSUMER_NAME = "notification-service";

    private final ProcessedMessageTracker processedMessageTracker;
    private final ObjectMapper objectMapper;

    public NotificationConsumer(ProcessedMessageTracker processedMessageTracker, ObjectMapper objectMapper) {
        this.processedMessageTracker = processedMessageTracker;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_EVENTS_QUEUE)
    public void handleOrderEvent(String messagePayload) {
        JsonNode json = objectMapper.readTree(messagePayload);

        if (json.get("eventId") == null) {
            throw new IllegalArgumentException("Malformed message, missing eventId: " + messagePayload);
        }

        UUID eventId = UUID.fromString(json.get("eventId").asString());
        String orderId = json.get("orderId").asString();
        String eventType = json.get("eventType").asString();

        boolean isNewEvent = processedMessageTracker.tryMarkAsProcessed(CONSUMER_NAME, eventId);

        if (!isNewEvent) {
            System.out.println("Duplicate event detected, skipping: " + eventId);
            return;
        }

        sendNotification(orderId, eventType);
    }

    private void sendNotification(String orderId, String eventType) {
        System.out.println("[NOTIFICATION] Order " + orderId + " -> " + eventType + " (simulated email sent)");
    }
}