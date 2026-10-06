package com.sagar.forgeorder.reconciliation.application;

import com.sagar.forgeorder.orders.domain.Order;
import com.sagar.forgeorder.orders.domain.OrderStatus;
import com.sagar.forgeorder.orders.persistence.OrderRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Component
public class ReconciliationScheduler {

    private final OrderRepository orderRepository;
    private final ReconciliationService reconciliationService;
    private final String workerId = "worker-" + UUID.randomUUID();

    public ReconciliationScheduler(OrderRepository orderRepository,
                                   ReconciliationService reconciliationService) {
        this.orderRepository = orderRepository;
        this.reconciliationService = reconciliationService;
    }

    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void runReconciliation() {
        List<Order> candidates = orderRepository.findByStatus(OrderStatus.RECONCILIATION_REQUIRED);

        for (Order order : candidates) {
            Instant now = Instant.now();
            Instant leaseUntil = now.plus(5, ChronoUnit.MINUTES);

            int acquired = orderRepository.tryAcquireLease(order.getId(), workerId, now, leaseUntil);

            if (acquired == 1) {
                try {
                    reconciliationService.reconcileOrder(order.getId());
                } finally {
                    releaseLeaseQuietly(order.getId());
                }
            }
        }
    }

    private void releaseLeaseQuietly(UUID orderId) {
        orderRepository.findById(orderId).ifPresent(order -> {
            order.releaseReconciliationLease();
            orderRepository.save(order);
        });
    }
}