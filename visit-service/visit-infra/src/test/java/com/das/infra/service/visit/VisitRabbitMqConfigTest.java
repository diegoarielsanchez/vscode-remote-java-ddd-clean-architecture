package com.das.infra.service.visit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.retry.RetryContext;
import org.springframework.retry.policy.SimpleRetryPolicy;

import com.das.infra.service.visit.events.InvalidIntegrationEventException;

class VisitRabbitMqConfigTest {

    private final VisitRabbitMqConfig config = new VisitRabbitMqConfig();

    @Test
    void consumerQueuesAreQuorumBoundedAndDeadLettered() {
        Queue hcp = config.visitHcpQueue();
        Map<String, Object> args = hcp.getArguments();

        assertEquals(VisitRabbitMqConfig.HCP_QUEUE, hcp.getName());
        assertTrue(hcp.isDurable());
        assertEquals("quorum", args.get("x-queue-type"));
        assertEquals(VisitRabbitMqConfig.DEAD_LETTER_EXCHANGE, args.get("x-dead-letter-exchange"));
        assertEquals("hcp", args.get("x-dead-letter-routing-key"));
        assertEquals(VisitRabbitMqConfig.MAX_QUEUE_LENGTH, ((Number) args.get("x-max-length")).intValue());
        assertEquals("reject-publish", args.get("x-overflow"));

        assertEquals("msr", config.visitMsrQueue().getArguments().get("x-dead-letter-routing-key"));
    }

    @Test
    void retriesTransientFailuresButNeverInvalidMessages() {
        SimpleRetryPolicy policy = VisitRabbitMqConfig.retryPolicy(3);

        assertTrue(canRetryAfter(policy, wrap(new DataAccessResourceFailureException("database down"))));
        assertFalse(canRetryAfter(policy, wrap(new AmqpRejectAndDontRequeueException("invalid",
                new InvalidIntegrationEventException("bad")))));
        assertFalse(canRetryAfter(policy, wrap(new MessageConversionException("bad json"))));
    }

    private static Throwable wrap(Throwable cause) {
        return new ListenerExecutionFailedException("listener failed", cause);
    }

    private static boolean canRetryAfter(SimpleRetryPolicy policy, Throwable failure) {
        RetryContext context = policy.open(null);
        policy.registerThrowable(context, failure);
        return policy.canRetry(context);
    }
}
