package com.das.infra.service.medicalsalesrep;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Objects;

@Configuration
@Profile("!dev")
public class MsrRabbitMqConfig {

    /** Durable topic exchange — created once, survives broker restarts. */
    @Bean
    TopicExchange msrEventsExchange() {
        return new TopicExchange(MsrAmqpEventPublisher.EXCHANGE, true, false);
    }

    @Bean
    Jackson2JsonMessageConverter msrJsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    RabbitTemplate msrRabbitTemplate(ConnectionFactory connectionFactory,
                                     Jackson2JsonMessageConverter msrJsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(Objects.requireNonNull(connectionFactory));
        template.setMessageConverter(Objects.requireNonNull(msrJsonMessageConverter));
        return template;
    }
}
