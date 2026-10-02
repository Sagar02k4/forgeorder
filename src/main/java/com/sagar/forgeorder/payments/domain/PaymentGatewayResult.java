package com.sagar.forgeorder.payments.domain;

import java.math.BigDecimal;

public record PaymentGatewayResult(
        GatewayOutcome outcome,
        String providerPaymentId,
        BigDecimal amount,
        String declineReason,
        String rawResponse
) {
    public enum GatewayOutcome {
        SUCCESS,
        DECLINED,
        TIMEOUT
    }

    public static PaymentGatewayResult success(String providerPaymentId, BigDecimal amount, String rawResponse) {
        return new PaymentGatewayResult(GatewayOutcome.SUCCESS, providerPaymentId, amount, null, rawResponse);
    }

    public static PaymentGatewayResult declined(BigDecimal amount, String declineReason, String rawResponse) {
        return new PaymentGatewayResult(GatewayOutcome.DECLINED, null, amount, declineReason, rawResponse);
    }

    public static PaymentGatewayResult timeout(BigDecimal amount, String rawResponse) {
        return new PaymentGatewayResult(GatewayOutcome.TIMEOUT, null, amount, null, rawResponse);
    }
}