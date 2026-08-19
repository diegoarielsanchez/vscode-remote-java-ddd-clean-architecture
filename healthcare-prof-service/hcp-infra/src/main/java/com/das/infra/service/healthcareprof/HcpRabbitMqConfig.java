package com.das.infra.service.healthcareprof;

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
public class HcpRabbitMqConfig {

    @Bean
    TopicExchange hcpEventsExchange() {
        return new TopicExchange("hcp.events", true, false);
    }

    @Bean
    Jackson2JsonMessageConverter hcpJsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    RabbitTemplate hcpRabbitTemplate(ConnectionFactory connectionFactory,
                                      Jackson2JsonMessageConverter hcpJsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(Objects.requireNonNull(connectionFactory));
        template.setMessageConverter(Objects.requireNonNull(hcpJsonMessageConverter));
        return template;
    }
}
