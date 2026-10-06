package com.sagar.forgeorder.webhooks.application;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@Component
public class WebhookSignatureVerifier {

    private static final String SHARED_SECRET = "mock-webhook-shared-secret";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    public String computeSignature(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(SHARED_SECRET.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC signature", e);
        }
    }

    public boolean verifySignature(String payload, String providedSignature) {
        String expectedSignature = computeSignature(payload);
        return expectedSignature.equals(providedSignature);
    }
}