package com.sagar.forgeorder.outbox.application;

import com.sagar.forgeorder.config.RabbitMQConfig;
import com.sagar.forgeorder.outbox.domain.OutboxEvent;
import com.sagar.forgeorder.outbox.domain.OutboxEventStatus;
import com.sagar.forgeorder.outbox.persistence.OutboxEventRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository, RabbitTemplate rabbitTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelay = 10000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatus(OutboxEventStatus.NEW);

        for (OutboxEvent event : pendingEvents) {
            try {
                rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_EVENTS_EXCHANGE, "", event.getPayload());
                event.markPublished();
            } catch (Exception e) {
                event.markFailedAttempt();
            }
            outboxEventRepository.save(event);
        }
    }
}