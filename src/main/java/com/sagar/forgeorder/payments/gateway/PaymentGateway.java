package com.sagar.forgeorder.payments.gateway;

import com.sagar.forgeorder.payments.domain.PaymentGatewayResult;

import java.math.BigDecimal;

public interface PaymentGateway {
    PaymentGatewayResult charge(String idempotencyKey, BigDecimal amount, String cardToken);
    PaymentGatewayResult queryStatus(String idempotencyKey);
}