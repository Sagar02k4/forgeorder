package com.sagar.forgeorder.orders.application;

import com.sagar.forgeorder.inventory.application.InventoryService;
import com.sagar.forgeorder.orders.domain.Order;
import com.sagar.forgeorder.orders.domain.OrderAuditEvent;
import com.sagar.forgeorder.orders.domain.OrderStatus;
import com.sagar.forgeorder.orders.persistence.OrderAuditEventRepository;
import com.sagar.forgeorder.orders.persistence.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderAuditEventRepository auditEventRepository;
    private final InventoryService inventoryService;

    public OrderService(OrderRepository orderRepository,
                        OrderAuditEventRepository auditEventRepository,
                        InventoryService inventoryService) {
        this.orderRepository = orderRepository;
        this.auditEventRepository = auditEventRepository;
        this.inventoryService = inventoryService;
    }

    @Transactional
    public Order createOrder(UUID customerId,
                             UUID productId,
                             int quantity,
                             BigDecimal subtotal,
                             BigDecimal tax,
                             String correlationId) {

        Order order = new Order(customerId, productId, quantity, subtotal, tax);
        orderRepository.save(order);   // <- YAHAN PEHLE SAVE KARO, taaki FK constraint satisfy ho

        OrderAuditEvent createdEvent = order.transitionTo(
                OrderStatus.CREATED, "CUSTOMER", customerId.toString(),
                correlationId, null, "Order created"
        );
        auditEventRepository.save(createdEvent);

        OrderAuditEvent reservationPendingEvent = order.transitionTo(
                OrderStatus.INVENTORY_RESERVATION_PENDING, "SYSTEM_WORKER", null,
                correlationId, null, "Requesting inventory reservation"
        );
        auditEventRepository.save(reservationPendingEvent);

        boolean reserved = inventoryService.reserveStock(productId, quantity);

        if (reserved) {
            OrderAuditEvent reservedEvent = order.transitionTo(
                    OrderStatus.INVENTORY_RESERVED, "SYSTEM_WORKER", null,
                    correlationId, null, "Inventory reserved successfully"
            );
            auditEventRepository.save(reservedEvent);
        } else {
            OrderAuditEvent unavailableEvent = order.transitionTo(
                    OrderStatus.INVENTORY_UNAVAILABLE, "SYSTEM_WORKER", null,
                    correlationId, null, "Insufficient stock"
            );
            auditEventRepository.save(unavailableEvent);

            OrderAuditEvent cancelledEvent = order.transitionTo(
                    OrderStatus.CANCELLED, "SYSTEM_WORKER", null,
                    correlationId, null, "Order cancelled due to unavailable inventory"
            );
            auditEventRepository.save(cancelledEvent);
        }

        return order;
    }
}