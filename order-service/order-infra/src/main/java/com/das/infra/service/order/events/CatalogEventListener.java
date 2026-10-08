package com.das.infra.service.order.events;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.das.infra.service.order.OrderRabbitMqConfig;

/**
 * Inbound AMQP adapter for the catalog's stock-reservation answers. Receives the raw message (no
 * type headers trusted), translates it through the anti-corruption layer and hands it to
 * {@link CatalogEventHandler}. Invalid messages are dead-lettered without retry; processing
 * failures are retried, then dead-lettered.
 */
@Component
@Profile("!dev")
public class CatalogEventListener {

    private static final Logger log = LoggerFactory.getLogger(CatalogEventListener.class);

    private final CatalogEventTranslator translator;
    private final CatalogEventHandler handler;

    public CatalogEventListener(CatalogEventTranslator translator, CatalogEventHandler handler) {
        this.translator = translator;
        this.handler = handler;
    }

    @RabbitListener(queues = OrderRabbitMqConfig.CATALOG_QUEUE, containerFactory = "orderRabbitListenerContainerFactory")
    public void onMessage(Message message) {
        Optional<StockReservationReply> reply;
        try {
            reply = translator.translate(message.getBody());
        } catch (InvalidIntegrationEventException e) {
            log.warn("Rejecting catalog event messageId={} reason={}",
                    sanitize(message.getMessageProperties().getMessageId()), e.getMessage());
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        }
        reply.ifPresent(handler::handle); // other catalog events: acknowledged and skipped
    }

    /** Message ids come from outside this service: strip anything that could forge log lines. */
    private static String sanitize(String value) {
        if (value == null) return null;
        String cleaned = value.replaceAll("[^A-Za-z0-9._:-]", "_");
        return cleaned.length() > 64 ? cleaned.substring(0, 64) : cleaned;
    }
}
