package com.das.infra.service.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.retry.RetryContext;
import org.springframework.retry.policy.SimpleRetryPolicy;

import com.das.infra.service.settlement.events.InvalidIntegrationEventException;

class SettlementRabbitMqConfigTest {

    private final SettlementRabbitMqConfig config = new SettlementRabbitMqConfig();

    @Test
    void msrQueueIsQuorumBoundedAndDeadLettered() {
        Map<String, Object> args = config.settlementMsrQueue().getArguments();
        assertEquals("quorum", args.get("x-queue-type"));
        assertEquals(SettlementRabbitMqConfig.DEAD_LETTER_EXCHANGE, args.get("x-dead-letter-exchange"));
        assertEquals("msr", args.get("x-dead-letter-routing-key"));
        assertEquals(SettlementRabbitMqConfig.MAX_QUEUE_LENGTH, ((Number) args.get("x-max-length")).intValue());
        assertEquals("reject-publish", args.get("x-overflow"));
    }

    @Test
    void retriesTransientFailuresButNeverInvalidMessages() {
        SimpleRetryPolicy policy = SettlementRabbitMqConfig.retryPolicy(3);
        assertTrue(canRetry(policy, new DataAccessResourceFailureException("db down")));
        assertFalse(canRetry(policy, new AmqpRejectAndDontRequeueException("bad", new InvalidIntegrationEventException("bad"))));
    }

    private static boolean canRetry(SimpleRetryPolicy policy, Throwable cause) {
        RetryContext context = policy.open(null);
        policy.registerThrowable(context, new ListenerExecutionFailedException("failed", cause));
        return policy.canRetry(context);
    }
}
