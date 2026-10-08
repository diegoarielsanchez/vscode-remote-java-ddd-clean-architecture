package com.das.infra.service.settlement.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.das.infra.service.settlement.SettlementRabbitMqConfig;

/**
 * Inbound AMQP adapter for {@code msr.events}. Receives the raw message (no type headers trusted),
 * translates it through the anti-corruption layer and hands it to {@link MsrEventHandler}.
 * Invalid messages are dead-lettered without retry; processing failures are retried, then
 * dead-lettered.
 */
@Component
public class MsrEventListener {

    private static final Logger log = LoggerFactory.getLogger(MsrEventListener.class);

    private final MsrEventTranslator translator;
    private final MsrEventHandler handler;

    public MsrEventListener(MsrEventTranslator translator, MsrEventHandler handler) {
        this.translator = translator;
        this.handler = handler;
    }

    @RabbitListener(queues = SettlementRabbitMqConfig.MSR_QUEUE, containerFactory = "settlementRabbitListenerContainerFactory")
    public void onMessage(Message message) {
        MsrStatusChange change;
        try {
            change = translator.translate(message.getBody());
        } catch (InvalidIntegrationEventException e) {
            log.warn("Rejecting msr event messageId={} reason={}",
                    sanitize(message.getMessageProperties().getMessageId()), e.getMessage());
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        }
        handler.handle(change);
    }

    /** Message ids come from outside this service: strip anything that could forge log lines. */
    private static String sanitize(String value) {
        if (value == null) return null;
        String cleaned = value.replaceAll("[^A-Za-z0-9._:-]", "_");
        return cleaned.length() > 64 ? cleaned.substring(0, 64) : cleaned;
    }
}
