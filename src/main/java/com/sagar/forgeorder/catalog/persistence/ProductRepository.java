package com.sagar.forgeorder.catalog.persistence;

import com.sagar.forgeorder.catalog.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
}