package com.sagar.forgeorder.payments.gateway;

import com.sagar.forgeorder.payments.domain.PaymentGatewayResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class MockPaymentGateway implements PaymentGateway {

    private static final String SUCCESS_PREFIX = "tok_success";
    private static final String DECLINE_PREFIX = "tok_decline";
    private static final String TIMEOUT_PREFIX = "tok_timeout";

    @Override
    public PaymentGatewayResult charge(String idempotencyKey, BigDecimal amount, String cardToken) {
        if (cardToken == null) {
            throw new IllegalArgumentException("cardToken must not be null");
        }

        if (cardToken.startsWith(SUCCESS_PREFIX)) {
            String providerPaymentId = "pay_" + UUID.randomUUID();
            return PaymentGatewayResult.success(
                    providerPaymentId,
                    amount,
                    "{\"status\":\"succeeded\",\"id\":\"" + providerPaymentId + "\"}"
            );
        }

        if (cardToken.startsWith(DECLINE_PREFIX)) {
            return PaymentGatewayResult.declined(
                    amount,
                    "insufficient_funds",
                    "{\"status\":\"declined\",\"reason\":\"insufficient_funds\"}"
            );
        }

        if (cardToken.startsWith(TIMEOUT_PREFIX)) {
            return PaymentGatewayResult.timeout(
                    amount,
                    "{\"status\":\"no_response\",\"note\":\"simulated timeout — charge may or may not have occurred provider-side\"}"
            );
        }

        throw new IllegalArgumentException(
                "Unrecognized mock card token: '" + cardToken + "'. " +
                        "Expected a token starting with 'tok_success', 'tok_decline', or 'tok_timeout'."
        );
    }
}