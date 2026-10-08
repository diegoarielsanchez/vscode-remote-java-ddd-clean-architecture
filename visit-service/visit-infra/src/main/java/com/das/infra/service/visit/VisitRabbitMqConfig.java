package com.das.infra.service.visit;

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
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.policy.SimpleRetryPolicy;

import com.das.infra.service.visit.events.InvalidIntegrationEventException;

/**
 * Consumer-side topology for the HCP and MSR events visit-service listens to.
 *
 * <ul>
 *   <li>Quorum queues (replicated, durable) with a dead-letter exchange: a message that cannot be
 *       processed ends up in {@code visit-service.*.dlq} instead of being requeued forever.</li>
 *   <li>Bounded queues ({@code x-max-length} + {@code reject-publish}): a flood makes the broker
 *       refuse new messages (the producer's outbox keeps them) rather than exhausting memory.</li>
 *   <li>Listener container: a few retries with backoff for transient failures, no retry for
 *       invalid messages, never requeue.</li>
 * </ul>
 * The queues are new ("v2") because RabbitMQ cannot change the arguments of an existing queue;
 * the old {@code visit-service.hcp.queue}/{@code visit-service.msr.queue} must be deleted once
 * drained (see README).
 */
@Configuration
public class VisitRabbitMqConfig {

    public static final String HCP_QUEUE = "visit-service.hcp.v2.queue";
    public static final String MSR_QUEUE = "visit-service.msr.v2.queue";
    public static final String DEAD_LETTER_EXCHANGE = "visit-service.dlx";
    public static final String HCP_DLQ = "visit-service.hcp.dlq";
    public static final String MSR_DLQ = "visit-service.msr.dlq";
    static final int MAX_QUEUE_LENGTH = 100_000;

    // ── Exchanges (must match the names declared in msr-infra / hcp-infra) ──

    @Bean
    public TopicExchange msrEventsExchange() {
        return new TopicExchange("msr.events", true, false);
    }

    @Bean
    public TopicExchange hcpEventsExchange() {
        return new TopicExchange("hcp.events", true, false);
    }

    @Bean
    public DirectExchange visitDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    // ── Queues ───────────────────────────────────────────────────────────────

    @Bean
    public Queue visitMsrQueue() {
        return consumerQueue(MSR_QUEUE, "msr");
    }

    @Bean
    public Queue visitHcpQueue() {
        return consumerQueue(HCP_QUEUE, "hcp");
    }

    @Bean
    public Queue visitMsrDeadLetterQueue() {
        return QueueBuilder.durable(MSR_DLQ).quorum().build();
    }

    @Bean
    public Queue visitHcpDeadLetterQueue() {
        return QueueBuilder.durable(HCP_DLQ).quorum().build();
    }

    private static Queue consumerQueue(String name, String deadLetterRoutingKey) {
        return QueueBuilder.durable(name)
                .quorum()
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(deadLetterRoutingKey)
                .maxLength(MAX_QUEUE_LENGTH)
                .overflow(QueueBuilder.Overflow.rejectPublish)
                .build();
    }

    // ── Bindings ─────────────────────────────────────────────────────────────

    @Bean
    public Binding msrBinding(Queue visitMsrQueue, TopicExchange msrEventsExchange) {
        return BindingBuilder.bind(visitMsrQueue).to(msrEventsExchange).with("msr.#");
    }

    @Bean
    public Binding hcpBinding(Queue visitHcpQueue, TopicExchange hcpEventsExchange) {
        return BindingBuilder.bind(visitHcpQueue).to(hcpEventsExchange).with("hcp.#");
    }

    @Bean
    public Binding msrDeadLetterBinding(Queue visitMsrDeadLetterQueue, DirectExchange visitDeadLetterExchange) {
        return BindingBuilder.bind(visitMsrDeadLetterQueue).to(visitDeadLetterExchange).with("msr");
    }

    @Bean
    public Binding hcpDeadLetterBinding(Queue visitHcpDeadLetterQueue, DirectExchange visitDeadLetterExchange) {
        return BindingBuilder.bind(visitHcpDeadLetterQueue).to(visitDeadLetterExchange).with("hcp");
    }

    // ── Message converter (MSR listener) ─────────────────────────────────────

    /**
     * Converts to the listener's declared parameter type only; {@code __TypeId__} headers sent by
     * producers are never used to pick a class (OWASP A08, insecure deserialization).
     */
    @Bean
    public Jackson2JsonMessageConverter visitJsonMessageConverter() {
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        typeMapper.setTrustedPackages(); // nothing beyond java.util / java.lang
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    @Bean
    @SuppressWarnings("null")
    public RabbitTemplate visitRabbitTemplate(ConnectionFactory connectionFactory,
                                               Jackson2JsonMessageConverter visitJsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(visitJsonMessageConverter);
        return template;
    }

    /**
     * Used by every visit-service {@code @RabbitListener}: transient failures are retried
     * ({@code visit.events.retry.max-attempts}, exponential backoff), invalid messages are not,
     * and exhausted or rejected messages are dead-lettered — never requeued in a loop.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory visitRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter visitJsonMessageConverter,
            @Value("${visit.events.retry.max-attempts:3}") int maxAttempts,
            @Value("${visit.events.prefetch:20}") int prefetch) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(visitJsonMessageConverter);
        factory.setRecoveryInterval(60_000L); // retry broker reconnect every 60 s when unavailable
        factory.setDefaultRequeueRejected(false);
        factory.setPrefetchCount(prefetch);
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
                InvalidIntegrationEventException.class, false,
                MessageConversionException.class, false), true, true);
    }
}
