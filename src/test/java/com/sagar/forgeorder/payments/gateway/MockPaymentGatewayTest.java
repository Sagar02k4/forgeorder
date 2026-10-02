package com.sagar.forgeorder.payments.gateway;

import com.sagar.forgeorder.payments.domain.PaymentGatewayResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockPaymentGatewayTest {

    private final MockPaymentGateway gateway = new MockPaymentGateway();

    @Test
    void successTokenReturnsSuccessOutcome() {
        PaymentGatewayResult result = gateway.charge("idem-1", new BigDecimal("100.00"), "tok_success_visa");

        assertThat(result.outcome()).isEqualTo(PaymentGatewayResult.GatewayOutcome.SUCCESS);
        assertThat(result.providerPaymentId()).isNotNull();
    }

    @Test
    void declineTokenReturnsDeclinedOutcome() {
        PaymentGatewayResult result = gateway.charge("idem-2", new BigDecimal("100.00"), "tok_decline_visa");

        assertThat(result.outcome()).isEqualTo(PaymentGatewayResult.GatewayOutcome.DECLINED);
        assertThat(result.providerPaymentId()).isNull();
        assertThat(result.declineReason()).isEqualTo("insufficient_funds");
    }

    @Test
    void timeoutTokenReturnsTimeoutOutcome() {
        PaymentGatewayResult result = gateway.charge("idem-3", new BigDecimal("100.00"), "tok_timeout_visa");

        assertThat(result.outcome()).isEqualTo(PaymentGatewayResult.GatewayOutcome.TIMEOUT);
        assertThat(result.providerPaymentId()).isNull();
    }

    @Test
    void unrecognizedTokenThrowsException() {
        assertThatThrownBy(() -> gateway.charge("idem-4", new BigDecimal("100.00"), "garbage_token"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}