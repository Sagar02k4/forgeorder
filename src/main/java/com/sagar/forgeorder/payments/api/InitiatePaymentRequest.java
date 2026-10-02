package com.sagar.forgeorder.payments.api;

import jakarta.validation.constraints.NotBlank;

public record InitiatePaymentRequest(
        @NotBlank(message = "cardToken is required")
        String cardToken
) {
}