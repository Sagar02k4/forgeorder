package com.sagar.forgeorder.orders.application;

import com.sagar.forgeorder.catalog.domain.Product;
import com.sagar.forgeorder.catalog.domain.ProductNotFoundException;
import com.sagar.forgeorder.catalog.persistence.ProductRepository;
import com.sagar.forgeorder.inventory.application.InventoryService;
import com.sagar.forgeorder.orders.domain.Order;
import com.sagar.forgeorder.orders.domain.OrderAuditEvent;
import com.sagar.forgeorder.orders.domain.OrderStatus;
import com.sagar.forgeorder.orders.persistence.OrderAuditEventRepository;
import com.sagar.forgeorder.orders.persistence.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderAuditEventRepository auditEventRepository;
    private final InventoryService inventoryService;
    @Autowired
    private final ProductRepository productRepository;

    public OrderService(OrderRepository orderRepository,
                        OrderAuditEventRepository auditEventRepository,
                        InventoryService inventoryService,
                        ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.auditEventRepository = auditEventRepository;
        this.inventoryService = inventoryService;
        this.productRepository = productRepository;
    }

    @Transactional
    public Order createOrder(UUID customerId,
                             UUID productId,
                             int quantity,
                             String correlationId) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(quantity));
        BigDecimal tax = subtotal.multiply(product.getTaxRate());

        Order order = new Order(customerId, productId, quantity, subtotal, tax);
        orderRepository.save(order);

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

        boolean reserved = inventoryService.reserveStock(order.getId(), productId, quantity);

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