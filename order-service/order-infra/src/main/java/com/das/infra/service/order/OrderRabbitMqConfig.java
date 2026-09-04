package com.das.infra.service.order;

import java.util.Objects;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!dev")
public class OrderRabbitMqConfig {

    /** Durable topic exchange — created once, survives broker restarts. */
    @Bean
    TopicExchange orderEventsExchange() {
        return new TopicExchange(OrderAmqpEventPublisher.EXCHANGE, true, false);
    }

    @Bean
    Jackson2JsonMessageConverter orderJsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    RabbitTemplate orderRabbitTemplate(ConnectionFactory connectionFactory,
                                        Jackson2JsonMessageConverter orderJsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(Objects.requireNonNull(connectionFactory));
        template.setMessageConverter(Objects.requireNonNull(orderJsonMessageConverter));
        return template;
    }
}
