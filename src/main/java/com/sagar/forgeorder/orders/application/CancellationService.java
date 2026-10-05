package com.sagar.forgeorder.orders.application;

import com.sagar.forgeorder.inventory.application.InventoryService;
import com.sagar.forgeorder.inventory.domain.InventoryReservation;
import com.sagar.forgeorder.inventory.domain.ReservationStatus;
import com.sagar.forgeorder.inventory.persistence.InventoryReservationRepository;
import com.sagar.forgeorder.orders.domain.*;
import com.sagar.forgeorder.orders.persistence.OrderAuditEventRepository;
import com.sagar.forgeorder.orders.persistence.OrderRepository;
import com.sagar.forgeorder.payments.domain.PaymentAttempt;
import com.sagar.forgeorder.refunds.application.RefundService;
import com.sagar.forgeorder.payments.persistence.PaymentAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CancellationService {

    private final OrderRepository orderRepository;
    private final OrderAuditEventRepository auditEventRepository;
    private final InventoryService inventoryService;
    private final InventoryReservationRepository reservationRepository;
    private final RefundService refundService;
    private final PaymentAttemptRepository paymentAttemptRepository;

    public CancellationService(OrderRepository orderRepository,
                               OrderAuditEventRepository auditEventRepository,
                               InventoryService inventoryService,
                               InventoryReservationRepository reservationRepository,
                               RefundService refundService,
                               PaymentAttemptRepository paymentAttemptRepository) {
        this.orderRepository = orderRepository;
        this.auditEventRepository = auditEventRepository;
        this.inventoryService = inventoryService;
        this.reservationRepository = reservationRepository;
        this.refundService = refundService;
        this.paymentAttemptRepository = paymentAttemptRepository;
    }

    public void cancelOrder(UUID orderId, String idempotencyKey, String correlationId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.FULFILLED) {
            handlePaidOrderCancellation(order, idempotencyKey, correlationId);
        } else if (order.getStatus() == OrderStatus.CREATED || order.getStatus() == OrderStatus.INVENTORY_RESERVED) {
            handleUnpaidOrderCancellation(order, correlationId);
        } else {
            throw new InvalidOrderStateTransitionException(order.getStatus(), OrderStatus.CANCELLATION_REQUESTED);
        }
    }

    @Transactional
    protected void handleUnpaidOrderCancellation(Order order, String correlationId) {
        OrderAuditEvent requestedEvent = order.transitionTo(
                OrderStatus.CANCELLATION_REQUESTED, "CUSTOMER", order.getCustomerId().toString(),
                correlationId, null, "Cancellation requested before payment"
        );
        auditEventRepository.save(requestedEvent);

        reservationRepository.findByOrderIdAndStatus(order.getId(), ReservationStatus.ACTIVE)
                .ifPresent(reservation -> inventoryService.releaseReservation(reservation.getId()));

        OrderAuditEvent cancelledEvent = order.transitionTo(
                OrderStatus.CANCELLED, "SYSTEM_WORKER", null,
                correlationId, null, "Order cancelled, inventory released"
        );
        auditEventRepository.save(cancelledEvent);

        orderRepository.save(order);
    }

    private void handlePaidOrderCancellation(Order order, String idempotencyKey, String correlationId) {
        PaymentAttempt paymentAttempt = paymentAttemptRepository
                .findFirstByOrderIdOrderByCreatedAtDesc(order.getId())
                .orElseThrow(() -> new IllegalStateException("No payment found for paid order: " + order.getId()));

        refundService.initiateRefund(order.getId(), idempotencyKey, paymentAttempt.getAmount(), correlationId);
    }
}