package com.das.inframySQL.service.visit;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Option B — RabbitMQ topology for the visit-service.
 * Binds durable queues to the MSR and HCP topic exchanges so that
 * domain events published by those services reach the local snapshot updaters.
 */
@Configuration
public class VisitRabbitMqConfig {

    // ── Exchanges (must match the names declared in msr-infra / hcp-infra) ──

    @Bean
    public TopicExchange msrEventsExchange() {
        return new TopicExchange("msr.events", true, false);
    }

    @Bean
    public TopicExchange hcpEventsExchange() {
        return new TopicExchange("hcp.events", true, false);
    }

    // ── Queues ───────────────────────────────────────────────────────────────

    @Bean
    public Queue visitMsrQueue() {
        return new Queue("visit-service.msr.queue", true);
    }

    @Bean
    public Queue visitHcpQueue() {
        return new Queue("visit-service.hcp.queue", true);
    }

    // ── Bindings ─────────────────────────────────────────────────────────────

    @Bean
    public Binding msrBinding(Queue visitMsrQueue, TopicExchange msrEventsExchange) {
        return BindingBuilder.bind(visitMsrQueue)
                .to(msrEventsExchange)
                .with("msr.#");
    }

    @Bean
    public Binding hcpBinding(Queue visitHcpQueue, TopicExchange hcpEventsExchange) {
        return BindingBuilder.bind(visitHcpQueue)
                .to(hcpEventsExchange)
                .with("hcp.#");
    }

    // ── Message converter ────────────────────────────────────────────────────

    @Bean
    public Jackson2JsonMessageConverter visitJsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate visitRabbitTemplate(ConnectionFactory connectionFactory,
                                               Jackson2JsonMessageConverter visitJsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(visitJsonMessageConverter);
        return template;
    }

    /**
     * Overrides the default {@code rabbitListenerContainerFactory} so that
     * all {@code @RabbitListener} methods use JSON deserialization.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory visitRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter visitJsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(visitJsonMessageConverter);
        return factory;
    }
}
