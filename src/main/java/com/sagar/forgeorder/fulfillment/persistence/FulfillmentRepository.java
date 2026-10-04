package com.sagar.forgeorder.fulfillment.persistence;

import com.sagar.forgeorder.fulfillment.domain.Fulfillment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FulfillmentRepository extends JpaRepository<Fulfillment, UUID> {
}