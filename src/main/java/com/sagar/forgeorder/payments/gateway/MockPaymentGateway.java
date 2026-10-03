package com.sagar.forgeorder.payments.gateway;

import com.sagar.forgeorder.payments.domain.PaymentGatewayResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MockPaymentGateway implements PaymentGateway {

    private static final String SUCCESS_PREFIX = "tok_success";
    private static final String DECLINE_PREFIX = "tok_decline";
    private static final String TIMEOUT_PREFIX = "tok_timeout";

    private final Map<String, PaymentGatewayResult> gatewaySideRecords = new ConcurrentHashMap<>();

    @Override
    public PaymentGatewayResult charge(String idempotencyKey, BigDecimal amount, String cardToken) {
        if (cardToken == null) {
            throw new IllegalArgumentException("cardToken must not be null");
        }

        if (cardToken.startsWith(SUCCESS_PREFIX)) {
            String providerPaymentId = "pay_" + UUID.randomUUID();
            PaymentGatewayResult result = PaymentGatewayResult.success(
                    providerPaymentId, amount,
                    "{\"status\":\"succeeded\",\"id\":\"" + providerPaymentId + "\"}"
            );
            gatewaySideRecords.put(idempotencyKey, result);
            return result;
        }

        if (cardToken.startsWith(DECLINE_PREFIX)) {
            PaymentGatewayResult result = PaymentGatewayResult.declined(
                    amount, "insufficient_funds",
                    "{\"status\":\"declined\",\"reason\":\"insufficient_funds\"}"
            );
            gatewaySideRecords.put(idempotencyKey, result);
            return result;
        }

        if (cardToken.startsWith(TIMEOUT_PREFIX)) {
            // Simulate: the charge actually succeeded on the provider's side,
            // but the response never reached us. The provider still records it.
            String providerPaymentId = "pay_" + UUID.randomUUID();
            PaymentGatewayResult actualOutcome = PaymentGatewayResult.success(
                    providerPaymentId, amount,
                    "{\"status\":\"succeeded\",\"id\":\"" + providerPaymentId + "\",\"note\":\"charge succeeded provider-side despite client timeout\"}"
            );
            gatewaySideRecords.put(idempotencyKey, actualOutcome);

            // But what WE return to the caller right now is TIMEOUT —
            // because from our side, we genuinely don't know yet.
            return PaymentGatewayResult.timeout(
                    amount,
                    "{\"status\":\"no_response\",\"note\":\"simulated timeout — caller does not know the outcome yet\"}"
            );
        }

        throw new IllegalArgumentException(
                "Unrecognized mock card token: '" + cardToken + "'. " +
                        "Expected a token starting with 'tok_success', 'tok_decline', or 'tok_timeout'."
        );
    }

    @Override
    public PaymentGatewayResult queryStatus(String idempotencyKey) {
        PaymentGatewayResult storedResult = gatewaySideRecords.get(idempotencyKey);

        if (storedResult == null) {
            return PaymentGatewayResult.notFound(
                    "{\"status\":\"not_found\",\"note\":\"no record for this idempotency key\"}"
            );
        }

        return storedResult;
    }
}