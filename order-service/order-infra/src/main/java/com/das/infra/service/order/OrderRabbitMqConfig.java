package com.das.infra.service.order;

import java.util.Objects;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Broker topology and template used by the outbox relay. Publisher confirms and returns are
 * enabled on the connection factory ({@code spring.rabbitmq.publisher-confirm-type=correlated},
 * {@code spring.rabbitmq.publisher-returns=true}); {@code mandatory} makes the broker hand back
 * messages that match no queue instead of silently dropping them.
 */
@Configuration
@Profile("!dev")
public class OrderRabbitMqConfig {

    @Bean
    TopicExchange orderEventsExchange() {
        return new TopicExchange("order.events", true, false);
    }

    @Bean
    RabbitTemplate orderRabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(Objects.requireNonNull(connectionFactory));
        template.setMandatory(true);
        return template;
    }
}
