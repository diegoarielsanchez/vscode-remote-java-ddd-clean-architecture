package com.das.infra.service.catalog;

import java.util.Map;
import java.util.Objects;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.retry.policy.SimpleRetryPolicy;

import com.das.infra.service.catalog.events.InvalidIntegrationEventException;

/**
 * Broker topology for product-catalog-service.
 *
 * <p><b>Producer:</b> the template used by the outbox relay. Publisher confirms and returns are
 * enabled on the connection factory ({@code spring.rabbitmq.publisher-confirm-type=correlated},
 * {@code spring.rabbitmq.publisher-returns=true}); {@code mandatory} makes the broker hand back
 * messages that match no queue instead of silently dropping them.
 *
 * <p><b>Consumer:</b> the stock saga's {@code order.events} (bound to {@code order.#}) — a quorum
 * queue with a dead-letter queue, bounded with {@code reject-publish}, and a listener container that
 * retries transient failures, never retries invalid messages and never requeues forever.
 */
@Configuration
@Profile("!dev")
public class ProductRabbitMqConfig {

    public static final String ORDER_QUEUE = "catalog-service.order.queue";
    public static final String DEAD_LETTER_EXCHANGE = "catalog-service.dlx";
    public static final String ORDER_DLQ = "catalog-service.order.dlq";
    static final int MAX_QUEUE_LENGTH = 100_000;

    @Bean
    TopicExchange catalogEventsExchange() {
        return new TopicExchange("catalog.events", true, false);
    }

    @Bean
    RabbitTemplate catalogRabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(Objects.requireNonNull(connectionFactory));
        template.setMandatory(true);
        return template;
    }

    @Bean
    TopicExchange catalogOrderEventsExchange() {
        return new TopicExchange("order.events", true, false);
    }

    @Bean
    DirectExchange catalogDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue catalogOrderQueue() {
        return QueueBuilder.durable(ORDER_QUEUE)
                .quorum()
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey("order")
                .maxLength(MAX_QUEUE_LENGTH)
                .overflow(QueueBuilder.Overflow.rejectPublish)
                .build();
    }

    @Bean
    Queue catalogOrderDeadLetterQueue() {
        return QueueBuilder.durable(ORDER_DLQ).quorum().build();
    }

    /**
     * Every order event, not only the three the saga needs: the order relay publishes with
     * {@code mandatory} and stops at an unroutable event, so an event type nobody else consumes yet
     * would hold back the ones queued behind it. The translator skips what the catalog ignores.
     */
    @Bean
    Binding catalogOrderEventsBinding(Queue catalogOrderQueue, TopicExchange catalogOrderEventsExchange) {
        return BindingBuilder.bind(catalogOrderQueue).to(catalogOrderEventsExchange).with("order.#");
    }

    @Bean
    Binding catalogOrderDeadLetterBinding(Queue catalogOrderDeadLetterQueue, DirectExchange catalogDeadLetterExchange) {
        return BindingBuilder.bind(catalogOrderDeadLetterQueue).to(catalogDeadLetterExchange).with("order");
    }

    @Bean
    SimpleRabbitListenerContainerFactory catalogRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            @Value("${catalog.events.retry.max-attempts:3}") int maxAttempts,
            @Value("${catalog.events.prefetch:20}") int prefetch,
            @Value("${catalog.events.listener.auto-startup:true}") boolean autoStartup) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setRecoveryInterval(30_000L);
        factory.setDefaultRequeueRejected(false);
        factory.setPrefetchCount(prefetch);
        factory.setAutoStartup(autoStartup);
        factory.setContainerCustomizer(container -> container.setPossibleAuthenticationFailureFatal(false));
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .retryPolicy(retryPolicy(maxAttempts))
                .backOffOptions(500, 2.0, 5_000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());
        return factory;
    }

    static SimpleRetryPolicy retryPolicy(int maxAttempts) {
        return new SimpleRetryPolicy(maxAttempts, Map.of(
                AmqpRejectAndDontRequeueException.class, false,
                InvalidIntegrationEventException.class, false), true, true);
    }
}
