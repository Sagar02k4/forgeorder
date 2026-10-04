package com.sagar.forgeorder.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String ORDER_EVENTS_EXCHANGE = "order.events.exchange";
    public static final String NOTIFICATION_QUEUE = "order.events.notification";
    public static final String FULFILLMENT_QUEUE = "order.events.fulfillment";

    public static final String NOTIFICATION_DLQ = "order.events.notification.dlq";
    public static final String FULFILLMENT_DLQ = "order.events.fulfillment.dlq";
    public static final String DLX_EXCHANGE = "order.events.dlx";

    @Bean
    public FanoutExchange orderEventsExchange() {
        return new FanoutExchange(ORDER_EVENTS_EXCHANGE);
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", NOTIFICATION_DLQ)
                .build();
    }

    @Bean
    public Queue fulfillmentQueue() {
        return QueueBuilder.durable(FULFILLMENT_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", FULFILLMENT_DLQ)
                .build();
    }

    @Bean
    public Binding notificationBinding() {
        return BindingBuilder.bind(notificationQueue()).to(orderEventsExchange());
    }

    @Bean
    public Binding fulfillmentBinding() {
        return BindingBuilder.bind(fulfillmentQueue()).to(orderEventsExchange());
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX_EXCHANGE);
    }

    @Bean
    public Queue notificationDeadLetterQueue() {
        return QueueBuilder.durable(NOTIFICATION_DLQ).build();
    }

    @Bean
    public Queue fulfillmentDeadLetterQueue() {
        return QueueBuilder.durable(FULFILLMENT_DLQ).build();
    }

    @Bean
    public Binding notificationDlqBinding() {
        return BindingBuilder.bind(notificationDeadLetterQueue()).to(deadLetterExchange()).with(NOTIFICATION_DLQ);
    }

    @Bean
    public Binding fulfillmentDlqBinding() {
        return BindingBuilder.bind(fulfillmentDeadLetterQueue()).to(deadLetterExchange()).with(FULFILLMENT_DLQ);
    }
}