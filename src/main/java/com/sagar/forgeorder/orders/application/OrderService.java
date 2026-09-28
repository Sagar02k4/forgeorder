package com.sagar.forgeorder.orders.application;

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

    public OrderService(OrderRepository orderRepository,
                        OrderAuditEventRepository auditEventRepository) {
        this.orderRepository = orderRepository;
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional
    public Order createOrder(UUID customerId,
                             BigDecimal subtotal,
                             BigDecimal tax,
                             String correlationId) {
        Order order = new Order(customerId, subtotal, tax);

        OrderAuditEvent auditEvent = order.transitionTo(
                OrderStatus.CREATED,
                "CUSTOMER",
                customerId.toString(),
                correlationId,
                null,
                "Order created"
        );

        orderRepository.save(order);
        auditEventRepository.save(auditEvent);

        return order;
    }
}