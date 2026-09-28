package com.sagar.forgeorder.common.idempotency;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class IdempotencyServiceConcurrencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private IdempotencyService idempotencyService;

    @Test
    void onlyOneConcurrentRequestWithSameKeyProceeds() throws InterruptedException, ExecutionException, TimeoutException {
        String sharedKey = "idem-" + UUID.randomUUID();
        String requestHash = idempotencyService.hashRequestBody("{\"items\":[]}");
        int numberOfThreads = 20;

        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger proceedCount = new AtomicInteger(0);
        AtomicInteger inProgressCount = new AtomicInteger(0);

        List<Future<?>> futures = new java.util.ArrayList<>();

        for (int i = 0; i < numberOfThreads; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // sab threads yahin ruke rahenge jab tak signal na mile
                    IdempotencyOutcome outcome = idempotencyService.beginOperation(
                            sharedKey, "CREATE_ORDER", "customer-1", requestHash
                    );
                    if (outcome.getType() == IdempotencyOutcome.Type.PROCEED) {
                        proceedCount.incrementAndGet();
                    } else if (outcome.getType() == IdempotencyOutcome.Type.IN_PROGRESS) {
                        inProgressCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        readyLatch.await();          // sab threads ready hone tak wait karo
        startLatch.countDown();      // ab sabko ek saath release karo

        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertThat(proceedCount.get()).isEqualTo(1);
        assertThat(inProgressCount.get()).isEqualTo(numberOfThreads - 1);
    }
}