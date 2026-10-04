package com.sagar.forgeorder.common.messaging;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProcessedMessageTracker {

    private final ProcessedMessageRepository processedMessageRepository;

    public ProcessedMessageTracker(ProcessedMessageRepository processedMessageRepository) {
        this.processedMessageRepository = processedMessageRepository;
    }

    @Transactional
    public boolean tryMarkAsProcessed(String consumerName, UUID eventId) {
        try {
            processedMessageRepository.save(new ProcessedMessage(consumerName, eventId));
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }
}