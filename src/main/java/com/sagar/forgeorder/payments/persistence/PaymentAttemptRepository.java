package com.sagar.forgeorder.payments.persistence;

import com.sagar.forgeorder.payments.domain.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

    Optional<PaymentAttempt> findFirstByOrderIdOrderByCreatedAtDesc(UUID orderId);
    Optional<PaymentAttempt> findByProviderPaymentId(String providerPaymentId);
}