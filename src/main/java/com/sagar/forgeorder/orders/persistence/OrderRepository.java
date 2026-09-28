package com.sagar.forgeorder.orders.persistence;

import com.sagar.forgeorder.orders.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
}