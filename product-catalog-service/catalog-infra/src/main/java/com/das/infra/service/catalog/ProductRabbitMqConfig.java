package com.das.infra.service.catalog;

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
public class ProductRabbitMqConfig {

    /** Durable topic exchange — created once, survives broker restarts. */
    @Bean
    TopicExchange catalogEventsExchange() {
        return new TopicExchange(ProductAmqpEventPublisher.EXCHANGE, true, false);
    }

    @Bean
    Jackson2JsonMessageConverter catalogJsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    RabbitTemplate catalogRabbitTemplate(ConnectionFactory connectionFactory,
                                          Jackson2JsonMessageConverter catalogJsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(Objects.requireNonNull(connectionFactory));
        template.setMessageConverter(Objects.requireNonNull(catalogJsonMessageConverter));
        return template;
    }
}
