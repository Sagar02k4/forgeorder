package com.sagar.forgeorder.fulfillment.application;

import com.sagar.forgeorder.common.messaging.ProcessedMessageTracker;
import com.sagar.forgeorder.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Component
public class FulfillmentConsumer {

    private static final String CONSUMER_NAME = "fulfillment-service";

    private final ProcessedMessageTracker processedMessageTracker;
    private final FulfillmentService fulfillmentService;
    private final ObjectMapper objectMapper;

    public FulfillmentConsumer(ProcessedMessageTracker processedMessageTracker,
                               FulfillmentService fulfillmentService,
                               ObjectMapper objectMapper) {
        this.processedMessageTracker = processedMessageTracker;
        this.fulfillmentService = fulfillmentService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitMQConfig.FULFILLMENT_QUEUE)
    public void handleOrderEvent(String messagePayload) {
        JsonNode json = objectMapper.readTree(messagePayload);

        if (json.get("eventId") == null) {
            throw new IllegalArgumentException("Malformed message, missing eventId: " + messagePayload);
        }

        UUID eventId = UUID.fromString(json.get("eventId").asString());
        UUID orderId = UUID.fromString(json.get("orderId").asString());
        String eventType = json.get("eventType").asString();

        if (!"ORDER_CONFIRMED".equals(eventType)) {
            return;
        }

        boolean isNewEvent = processedMessageTracker.tryMarkAsProcessed(CONSUMER_NAME, eventId);

        if (!isNewEvent) {
            System.out.println("Duplicate fulfillment event detected, skipping: " + eventId);
            return;
        }

        fulfillmentService.createShipment(orderId);
    }
}