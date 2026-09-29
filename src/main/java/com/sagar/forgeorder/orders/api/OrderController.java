package com.sagar.forgeorder.orders.api;

import com.sagar.forgeorder.common.api.ErrorResponse;
import com.sagar.forgeorder.orders.domain.OrderNotFoundException;
import com.sagar.forgeorder.orders.persistence.OrderRepository;
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

    public OrderController(OrderService orderService,
                           IdempotencyService idempotencyService,
                           ObjectMapper objectMapper, OrderRepository orderRepository) {
        this.orderService = orderService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
        this.orderRepository = orderRepository;
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
                request.customerId(), request.subtotal(), request.tax(), correlationId
        );

        OrderResponse response = OrderResponse.from(order);
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

        return ResponseEntity.ok(OrderResponse.from(order));
    }
}