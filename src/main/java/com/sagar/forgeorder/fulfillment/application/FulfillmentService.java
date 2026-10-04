package com.sagar.forgeorder.fulfillment.application;

import com.sagar.forgeorder.fulfillment.domain.Fulfillment;
import com.sagar.forgeorder.fulfillment.persistence.FulfillmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class FulfillmentService {

    private final FulfillmentRepository fulfillmentRepository;

    public FulfillmentService(FulfillmentRepository fulfillmentRepository) {
        this.fulfillmentRepository = fulfillmentRepository;
    }

    @Transactional
    public void createShipment(UUID orderId) {
        String shipmentReference = "ship_" + UUID.randomUUID();

        Fulfillment fulfillment = new Fulfillment(orderId);
        fulfillment.markShipped(shipmentReference);
        fulfillmentRepository.save(fulfillment);

        System.out.println("[FULFILLMENT] Order " + orderId + " shipped. Reference: " + shipmentReference);
    }
}