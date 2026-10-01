package com.sagar.forgeorder.inventory.application;

import com.sagar.forgeorder.inventory.persistence.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional
    public boolean reserveStock(UUID productId, int quantity) {
        int rowsUpdated = inventoryRepository.tryReserveStock(productId, quantity, Instant.now());
        return rowsUpdated == 1;
    }
}