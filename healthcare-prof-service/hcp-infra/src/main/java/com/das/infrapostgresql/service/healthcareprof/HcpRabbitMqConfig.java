package com.das.infrapostgresql.service.healthcareprof;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
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
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(hcpJsonMessageConverter);
        return template;
    }
}
