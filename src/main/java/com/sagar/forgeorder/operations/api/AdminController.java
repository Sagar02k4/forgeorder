package com.sagar.forgeorder.operations.api;

import com.sagar.forgeorder.orders.api.OrderResponse;
import com.sagar.forgeorder.orders.domain.Order;
import com.sagar.forgeorder.orders.domain.OrderStatus;
import com.sagar.forgeorder.orders.persistence.OrderRepository;
import com.sagar.forgeorder.reconciliation.application.ReconciliationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminController {

    private final OrderRepository orderRepository;
    private final ReconciliationService reconciliationService;

    public AdminController(OrderRepository orderRepository, ReconciliationService reconciliationService) {
        this.orderRepository = orderRepository;
        this.reconciliationService = reconciliationService;
    }

    @GetMapping
    public List<OrderResponse> getOrdersByStatus(@RequestParam OrderStatus status) {
        List<Order> orders = orderRepository.findByStatus(status);
        return orders.stream()
                .map(order -> new OrderResponse(
                        order.getId(), order.getCustomerId(), order.getProductId(), order.getQuantity(),
                        order.getStatus(), order.getSubtotal(), order.getTax(), order.getTotalAmount(),
                        order.getCreatedAt(), order.getUpdatedAt(), null, null
                ))
                .toList();
    }

    @PostMapping("/{id}/reconcile")
    public String triggerReconciliation(@PathVariable UUID id) {
        reconciliationService.reconcileOrder(id);
        return "Reconciliation triggered for order: " + id;
    }
}