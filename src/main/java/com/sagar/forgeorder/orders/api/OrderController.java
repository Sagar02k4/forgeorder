package com.sagar.forgeorder.orders.api;

import com.sagar.forgeorder.common.api.ErrorResponse;
import com.sagar.forgeorder.inventory.persistence.InventoryRepository;
import com.sagar.forgeorder.orders.domain.OrderNotFoundException;
import com.sagar.forgeorder.orders.persistence.OrderRepository;
import com.sagar.forgeorder.payments.api.InitiatePaymentRequest;
import com.sagar.forgeorder.payments.api.PaymentResponse;
import com.sagar.forgeorder.payments.application.PaymentService;
import com.sagar.forgeorder.payments.persistence.PaymentAttemptRepository;
import com.sagar.forgeorder.refunds.api.InitiateRefundRequest;
import com.sagar.forgeorder.refunds.application.RefundService;
import tools.jackson.databind.ObjectMapper;
import com.sagar.forgeorder.common.idempotency.IdempotencyOutcome;
import com.sagar.forgeorder.common.idempotency.IdempotencyService;
import com.sagar.forgeorder.orders.application.OrderService;
import com.sagar.forgeorder.orders.domain.Order;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    private final InventoryRepository inventoryRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final RefundService refundService;

    public OrderController(OrderService orderService,
                           IdempotencyService idempotencyService,
                           ObjectMapper objectMapper, OrderRepository orderRepository, PaymentService paymentService, InventoryRepository inventoryRepository, PaymentAttemptRepository paymentAttemptRepository, RefundService refundService) {
        this.orderService = orderService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
        this.orderRepository = orderRepository;
        this.paymentService = paymentService;
        this.inventoryRepository = inventoryRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.refundService = refundService;
    }

    @PostMapping
    public ResponseEntity<?> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationIdHeader) {

        String correlationId = (correlationIdHeader != null) ? correlationIdHeader : UUID.randomUUID().toString();
        String requestHash = idempotencyService.hashRequestBody(serializeToJson(request));

        IdempotencyOutcome outcome = idempotencyService.beginOperation(
                idempotencyKey, "CREATE_ORDER", request.customerId().toString(), requestHash
        );

        return switch (outcome.getType()) {
            case CONFLICT -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(
                            "IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD",
                            "The Idempotency-Key belongs to a request with different content.",
                            correlationId
                    ));

            case IN_PROGRESS -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(
                            "REQUEST_IN_PROGRESS",
                            "A request with this Idempotency-Key is already being processed.",
                            correlationId
                    ));

            case ALREADY_COMPLETED -> ResponseEntity.status(outcome.getStoredStatusCode())
                    .body(rawJsonBody(outcome.getStoredResponseBody()));

            case PROCEED -> handleNewOrderCreation(request, idempotencyKey, correlationId);
        };
    }

    private ResponseEntity<?> handleNewOrderCreation(CreateOrderRequest request,
                                                     String idempotencyKey,
                                                     String correlationId) {
        Order order = orderService.createOrder(
                request.customerId(), request.productId(), request.quantity(), correlationId
        );

        OrderResponse response = buildOrderResponse(order);
        String responseJson = serializeToJson(response);

        idempotencyService.completeOperation(idempotencyKey, HttpStatus.CREATED.value(), responseJson);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private String serializeToJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize object to JSON", e);
        }
    }

    private Object rawJsonBody(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse stored response JSON", e);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));

        return ResponseEntity.ok(buildOrderResponse(order));
    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<?> initiatePayment(
            @PathVariable UUID id,
            @Valid @RequestBody InitiatePaymentRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationIdHeader) {

        String correlationId = (correlationIdHeader != null) ? correlationIdHeader : UUID.randomUUID().toString();
        String requestHash = idempotencyService.hashRequestBody(serializeToJson(request));

        IdempotencyOutcome outcome = idempotencyService.beginOperation(
                idempotencyKey, "INITIATE_PAYMENT", id.toString(), requestHash
        );

        return switch (outcome.getType()) {
            case CONFLICT -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(
                            "IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD",
                            "The Idempotency-Key belongs to a request with different content.",
                            correlationId
                    ));

            case IN_PROGRESS -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(
                            "REQUEST_IN_PROGRESS",
                            "A request with this Idempotency-Key is already being processed.",
                            correlationId
                    ));

            case ALREADY_COMPLETED -> ResponseEntity.status(outcome.getStoredStatusCode())
                    .body(rawJsonBody(outcome.getStoredResponseBody()));

            case PROCEED -> handlePaymentInitiation(id, request, idempotencyKey, correlationId);
        };
    }

    private ResponseEntity<?> handlePaymentInitiation(UUID orderId,
                                                      InitiatePaymentRequest request,
                                                      String idempotencyKey,
                                                      String correlationId) {
        paymentService.initiatePayment(orderId, idempotencyKey, request.cardToken(), correlationId);

        Order updatedOrder = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        PaymentResponse response = new PaymentResponse(
                updatedOrder.getId(),
                updatedOrder.getStatus(),
                "Payment processed with outcome: " + updatedOrder.getStatus()
        );

        String responseJson = serializeToJson(response);
        idempotencyService.completeOperation(idempotencyKey, HttpStatus.OK.value(), responseJson);

        return ResponseEntity.ok(response);
    }

    private OrderResponse buildOrderResponse(Order order) {
        OrderResponse.InventorySummary inventorySummary = inventoryRepository.findById(order.getProductId())
                .map(inv -> new OrderResponse.InventorySummary(inv.getAvailableQuantity(), inv.getReservedQuantity()))
                .orElse(null);

        OrderResponse.PaymentSummary paymentSummary = paymentAttemptRepository
                .findFirstByOrderIdOrderByCreatedAtDesc(order.getId())
                .map(pa -> new OrderResponse.PaymentSummary(
                        pa.getStatus().name(), pa.getProviderPaymentId(), pa.getDeclineReason()))
                .orElse(null);

        return OrderResponse.from(order, inventorySummary, paymentSummary);
    }

    @PostMapping("/{id}/refunds")
    public ResponseEntity<?> initiateRefund(
            @PathVariable UUID id,
            @Valid @RequestBody InitiateRefundRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationIdHeader) {

        String correlationId = (correlationIdHeader != null) ? correlationIdHeader : UUID.randomUUID().toString();
        String requestHash = idempotencyService.hashRequestBody(serializeToJson(request));

        IdempotencyOutcome outcome = idempotencyService.beginOperation(
                idempotencyKey, "INITIATE_REFUND", id.toString(), requestHash
        );

        return switch (outcome.getType()) {
            case CONFLICT -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(
                            "IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD",
                            "The Idempotency-Key belongs to a request with different content.",
                            correlationId
                    ));

            case IN_PROGRESS -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(
                            "REQUEST_IN_PROGRESS",
                            "A request with this Idempotency-Key is already being processed.",
                            correlationId
                    ));

            case ALREADY_COMPLETED -> ResponseEntity.status(outcome.getStoredStatusCode())
                    .body(rawJsonBody(outcome.getStoredResponseBody()));

            case PROCEED -> handleRefundInitiation(id, request, idempotencyKey, correlationId);
        };
    }

    private ResponseEntity<?> handleRefundInitiation(UUID orderId,
                                                     InitiateRefundRequest request,
                                                     String idempotencyKey,
                                                     String correlationId) {
        refundService.initiateRefund(orderId, idempotencyKey, request.amount(), correlationId);

        Order updatedOrder = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        PaymentResponse response = new PaymentResponse(
                updatedOrder.getId(),
                updatedOrder.getStatus(),
                "Refund processed, order status: " + updatedOrder.getStatus()
        );

        String responseJson = serializeToJson(response);
        idempotencyService.completeOperation(idempotencyKey, HttpStatus.OK.value(), responseJson);

        return ResponseEntity.ok(response);
    }
}