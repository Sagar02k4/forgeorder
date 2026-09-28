package com.sagar.forgeorder.common.idempotency;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;

    public IdempotencyService(IdempotencyRecordRepository repository) {
        this.repository = repository;
    }

    public String hashRequestBody(String rawRequestBody) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawRequestBody.getBytes());
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public IdempotencyOutcome beginOperation(String idempotencyKey,
                                             String operationType,
                                             String actorId,
                                             String requestHash) {
        Optional<IdempotencyRecord> existing = repository.findById(idempotencyKey);

        if (existing.isPresent()) {
            return handleExistingRecord(existing.get(), requestHash);
        }

        boolean insertedByMe = tryInsertNewRecord(idempotencyKey, operationType, actorId, requestHash);

        if (insertedByMe) {
            return IdempotencyOutcome.proceed();
        }

        // Someone else won the race — re-fetch and handle it as an existing record.
        IdempotencyRecord raceWinner = repository.findById(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotency record vanished immediately after constraint violation"));
        return handleExistingRecord(raceWinner, requestHash);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryInsertNewRecord(String idempotencyKey,
                                      String operationType,
                                      String actorId,
                                      String requestHash) {
        try {
            IdempotencyRecord newRecord = new IdempotencyRecord(
                    idempotencyKey,
                    operationType,
                    actorId,
                    requestHash,
                    Instant.now().plus(24, ChronoUnit.HOURS)
            );
            repository.saveAndFlush(newRecord);
            return true;

        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    @Transactional
    public void completeOperation(String idempotencyKey, int statusCode, String responseBody) {
        IdempotencyRecord record = repository.findById(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotency record not found when completing: " + idempotencyKey));
        record.markSucceeded(statusCode, responseBody);
        repository.save(record);
    }

    private IdempotencyOutcome handleExistingRecord(IdempotencyRecord record, String requestHash) {
        if (!record.getRequestHash().equals(requestHash)) {
            return IdempotencyOutcome.conflict();
        }

        return switch (record.getStatus()) {
            case IN_PROGRESS -> IdempotencyOutcome.inProgress();
            case SUCCEEDED, FAILED -> IdempotencyOutcome.alreadyCompleted(
                    record.getResponseStatusCode(),
                    record.getResponseBody()
            );
        };
    }
}