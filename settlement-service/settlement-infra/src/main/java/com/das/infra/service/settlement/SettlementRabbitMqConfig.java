package com.das.infra.service.settlement;

import java.util.Map;

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
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.policy.SimpleRetryPolicy;

import com.das.infra.service.settlement.events.InvalidIntegrationEventException;

/**
 * Consumer topology for the {@code msr.events} settlement-service listens to: a quorum queue with a
 * dead-letter queue, bounded with {@code reject-publish}, and a listener container that retries
 * transient failures, never retries invalid messages and never requeues forever.
 *
 * <p>Broker problems — including refused credentials — do not stop the service from starting: the
 * container keeps reconnecting and {@link MedicalSalesRepStatusAdapter} falls back to the HTTP
 * check meanwhile.
 */
@Configuration
public class SettlementRabbitMqConfig {

    public static final String MSR_QUEUE = "settlement-service.msr.queue";
    public static final String DEAD_LETTER_EXCHANGE = "settlement-service.dlx";
    public static final String MSR_DLQ = "settlement-service.msr.dlq";
    static final int MAX_QUEUE_LENGTH = 100_000;

    @Bean
    public TopicExchange settlementMsrEventsExchange() {
        return new TopicExchange("msr.events", true, false);
    }

    @Bean
    public DirectExchange settlementDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue settlementMsrQueue() {
        return QueueBuilder.durable(MSR_QUEUE)
                .quorum()
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey("msr")
                .maxLength(MAX_QUEUE_LENGTH)
                .overflow(QueueBuilder.Overflow.rejectPublish)
                .build();
    }

    @Bean
    public Queue settlementMsrDeadLetterQueue() {
        return QueueBuilder.durable(MSR_DLQ).quorum().build();
    }

    @Bean
    public Binding settlementMsrBinding(Queue settlementMsrQueue, TopicExchange settlementMsrEventsExchange) {
        return BindingBuilder.bind(settlementMsrQueue).to(settlementMsrEventsExchange).with("msr.#");
    }

    @Bean
    public Binding settlementMsrDeadLetterBinding(Queue settlementMsrDeadLetterQueue,
                                                  DirectExchange settlementDeadLetterExchange) {
        return BindingBuilder.bind(settlementMsrDeadLetterQueue).to(settlementDeadLetterExchange).with("msr");
    }

    @Bean
    public SimpleRabbitListenerContainerFactory settlementRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            @Value("${settlement.events.retry.max-attempts:3}") int maxAttempts,
            @Value("${settlement.events.prefetch:20}") int prefetch) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setRecoveryInterval(30_000L);
        factory.setDefaultRequeueRejected(false);
        factory.setPrefetchCount(prefetch);
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
