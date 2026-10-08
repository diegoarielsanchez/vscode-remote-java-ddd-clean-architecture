package com.das.infra.service.catalog.events;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.das.infra.service.catalog.ProductRabbitMqConfig;

/**
 * Inbound AMQP adapter for {@code order.events}, the catalog's side of the stock saga. Receives the
 * raw message (no type headers trusted), translates it through the anti-corruption layer and hands
 * it to {@link OrderEventHandler}. Invalid messages are dead-lettered without retry; processing
 * failures are retried, then dead-lettered.
 */
@Component
@Profile("!dev")
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final OrderEventTranslator translator;
    private final OrderEventHandler handler;

    public OrderEventListener(OrderEventTranslator translator, OrderEventHandler handler) {
        this.translator = translator;
        this.handler = handler;
    }

    @RabbitListener(queues = ProductRabbitMqConfig.ORDER_QUEUE, containerFactory = "catalogRabbitListenerContainerFactory")
    public void onMessage(Message message) {
        Optional<OrderSagaEvent> event;
        try {
            event = translator.translate(message.getBody());
        } catch (InvalidIntegrationEventException e) {
            log.warn("Rejecting order event messageId={} reason={}",
                    sanitize(message.getMessageProperties().getMessageId()), e.getMessage());
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        }
        event.ifPresent(handler::handle); // other order events: acknowledged and skipped
    }

    /** Message ids come from outside this service: strip anything that could forge log lines. */
    private static String sanitize(String value) {
        if (value == null) return null;
        String cleaned = value.replaceAll("[^A-Za-z0-9._:-]", "_");
        return cleaned.length() > 64 ? cleaned.substring(0, 64) : cleaned;
    }
}
