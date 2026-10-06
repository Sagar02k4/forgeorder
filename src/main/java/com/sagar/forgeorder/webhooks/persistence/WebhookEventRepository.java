package com.sagar.forgeorder.webhooks.persistence;

import com.sagar.forgeorder.webhooks.domain.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, String> {
}