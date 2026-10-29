package com.das.infra.service.order;

import java.time.Duration;
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
import org.springframework.core.retry.RetryPolicy;

import com.das.infra.service.order.events.InvalidIntegrationEventException;

/**
 * Broker topology for order-service.
 *
 * <p><b>Producer:</b> the template used by the outbox relay. Publisher confirms and returns are
 * enabled on the connection factory ({@code spring.rabbitmq.publisher-confirm-type=correlated},
 * {@code spring.rabbitmq.publisher-returns=true}); {@code mandatory} makes the broker hand back
 * messages that match no queue instead of silently dropping them.
 *
 * <p><b>Consumer:</b> the stock saga's answers from {@code catalog.events} (bound to {@code catalog.#}) — a quorum queue with a
 * dead-letter queue, bounded with {@code reject-publish}, and a listener container that retries
 * transient failures, never retries invalid messages and never requeues forever.
 */
@Configuration
@Profile("!dev")
public class OrderRabbitMqConfig {

    public static final String CATALOG_QUEUE = "order-service.catalog.queue";
    public static final String DEAD_LETTER_EXCHANGE = "order-service.dlx";
    public static final String CATALOG_DLQ = "order-service.catalog.dlq";
    static final int MAX_QUEUE_LENGTH = 100_000;

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

    @Bean
    TopicExchange orderCatalogEventsExchange() {
        return new TopicExchange("catalog.events", true, false);
    }

    @Bean
    DirectExchange orderDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue orderCatalogQueue() {
        return QueueBuilder.durable(CATALOG_QUEUE)
                .quorum()
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey("catalog")
                .maxLength(MAX_QUEUE_LENGTH)
                .overflow(QueueBuilder.Overflow.rejectPublish)
                .build();
    }

    @Bean
    Queue orderCatalogDeadLetterQueue() {
        return QueueBuilder.durable(CATALOG_DLQ).quorum().build();
    }

    /**
     * Every catalog event, not only the reservation answers: the catalog relay publishes with
     * {@code mandatory} and stops at an unroutable event, so an event type nobody else consumes yet
     * would hold back the answers queued behind it. The translator skips what order-service ignores.
     */
    @Bean
    Binding orderCatalogEventsBinding(Queue orderCatalogQueue, TopicExchange orderCatalogEventsExchange) {
        return BindingBuilder.bind(orderCatalogQueue).to(orderCatalogEventsExchange).with("catalog.#");
    }

    @Bean
    Binding orderCatalogDeadLetterBinding(Queue orderCatalogDeadLetterQueue, DirectExchange orderDeadLetterExchange) {
        return BindingBuilder.bind(orderCatalogDeadLetterQueue).to(orderDeadLetterExchange).with("catalog");
    }

    @Bean
    SimpleRabbitListenerContainerFactory orderRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            @Value("${order.events.retry.max-attempts:3}") int maxAttempts,
            @Value("${order.events.prefetch:20}") int prefetch,
            @Value("${order.events.listener.auto-startup:true}") boolean autoStartup) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setRecoveryInterval(30_000L);
        factory.setDefaultRequeueRejected(false);
        factory.setPrefetchCount(prefetch);
        factory.setAutoStartup(autoStartup);
        factory.setContainerCustomizer(container -> container.setPossibleAuthenticationFailureFatal(false));
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .retryPolicy(retryPolicy(maxAttempts))
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());
        return factory;
    }

    /**
     * At most {@code maxAttempts} deliveries in all, backing off from 500 ms to 5 s, unless the failure was caused by
     * an invalid message: retrying cannot fix that.
     */
    static RetryPolicy retryPolicy(int maxAttempts) {
        return RetryPolicy.builder()
                .maxRetries(maxAttempts - 1)
                .delay(Duration.ofMillis(500))
                .multiplier(2.0)
                .maxDelay(Duration.ofSeconds(5))
                .predicate(failure -> !invalidMessage(failure))
                .build();
    }

    private static boolean invalidMessage(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof AmqpRejectAndDontRequeueException
                    || t instanceof InvalidIntegrationEventException) {
                return true;
            }
        }
        return false;
    }
}
