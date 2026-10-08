package com.das.infra.service.medicalsalesrep.outbox;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publishes outbox rows to the {@code msr.events} exchange with publisher confirms.
 *
 * <p>A row is marked published only after the broker confirms it (ack) and did not return it as
 * unroutable. On the first failure the batch stops, so later events of the same aggregate are not
 * published ahead of an earlier one; the row is retried on the next run. Delivery is therefore
 * at-least-once: consumers deduplicate on {@code eventId}.
 */
@Component
@Profile("!dev")
@ConditionalOnProperty(name = "msr.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class MsrOutboxRelay {

    public static final String EXCHANGE = "msr.events";
    private static final Logger log = LoggerFactory.getLogger(MsrOutboxRelay.class);

    private final OutboxEventJpaRepository outbox;
    private final RabbitTemplate rabbit;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final int batchSize;
    private final long confirmTimeoutMs;
    private final Duration retention;

    @Autowired
    public MsrOutboxRelay(OutboxEventJpaRepository outbox,
                          RabbitTemplate msrRabbitTemplate,
                          PlatformTransactionManager transactionManager,
                          @Value("${msr.outbox.relay.batch-size:50}") int batchSize,
                          @Value("${msr.outbox.relay.confirm-timeout-ms:5000}") long confirmTimeoutMs,
                          @Value("${msr.outbox.retention:P7D}") Duration retention) {
        this(outbox, msrRabbitTemplate, new TransactionTemplate(transactionManager), Clock.systemUTC(),
                batchSize, confirmTimeoutMs, retention);
    }

    MsrOutboxRelay(OutboxEventJpaRepository outbox, RabbitTemplate rabbit, TransactionTemplate tx, Clock clock,
                   int batchSize, long confirmTimeoutMs, Duration retention) {
        this.outbox = outbox;
        this.rabbit = rabbit;
        this.tx = tx;
        this.clock = clock;
        this.batchSize = batchSize;
        this.confirmTimeoutMs = confirmTimeoutMs;
        this.retention = retention;
    }

    @Scheduled(initialDelayString = "${msr.outbox.relay.initial-delay-ms:5000}",
               fixedDelayString = "${msr.outbox.relay.interval-ms:1000}")
    public void scheduledRelay() {
        try {
            relayBatch();
        } catch (RuntimeException e) {
            log.error("Outbox relay run failed: {}", e.getMessage());
        }
    }

    /** Publishes up to one batch; returns how many events were confirmed by the broker. */
    public int relayBatch() {
        Integer published = tx.execute(status -> {
            List<OutboxEventEntity> batch = outbox.lockPending(PageRequest.of(0, batchSize));
            int confirmed = 0;
            for (OutboxEventEntity event : batch) {
                String failure = publish(event);
                if (failure != null) {
                    event.recordFailure(failure);
                    log.warn("Outbox publish failed: eventId={} type={} aggregateId={} attempts={} reason={}",
                            event.getId(), event.getEventType(), event.getAggregateId(), event.getAttempts(), failure);
                    break;
                }
                event.markPublished(clock.instant());
                confirmed++;
            }
            return confirmed;
        });
        return published == null ? 0 : published;
    }

    /** Returns null on success, otherwise the failure reason. */
    private String publish(OutboxEventEntity event) {
        Message message = MessageBuilder.withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(event.getId().toString())
                .setType(event.getEventType())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build(); // raw bytes: no __TypeId__ header for consumers to (mis)trust
        CorrelationData correlation = new CorrelationData(event.getId().toString());
        try {
            rabbit.send(EXCHANGE, event.getEventType(), message, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(confirmTimeoutMs, TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) return "nack: " + confirm.getReason();
            if (correlation.getReturned() != null) return "unroutable: " + correlation.getReturned().getReplyText();
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "interrupted";
        } catch (Exception e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    /** Published rows are kept for a while for troubleshooting, then removed. */
    @Scheduled(cron = "${msr.outbox.cleanup.cron:0 15 * * * *}")
    public void scheduledCleanup() {
        Integer deleted = tx.execute(status -> outbox.deletePublishedBefore(clock.instant().minus(retention)));
        if (deleted != null && deleted > 0) log.info("Outbox cleanup removed {} published events", deleted);
    }
}
