package com.das.inframySQL.service.medicalsalesrep;

import java.time.Instant;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.medicalsalesrep.events.MsrActivatedEvent;
import com.das.cleanddd.domain.medicalsalesrep.events.MsrCreatedEvent;
import com.das.cleanddd.domain.medicalsalesrep.events.MsrDeactivatedEvent;
import com.das.cleanddd.domain.medicalsalesrep.events.MsrDomainEvent;
import com.das.cleanddd.domain.medicalsalesrep.events.MsrUpdatedEvent;
import com.das.cleanddd.domain.medicalsalesrep.ports.IMsrEventPublisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Primary
@Service
public class MsrAmqpEventPublisher implements IMsrEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(MsrAmqpEventPublisher.class);
    static final String EXCHANGE = "msr.events";

    private final RabbitTemplate msrRabbitTemplate;

    public MsrAmqpEventPublisher(RabbitTemplate msrRabbitTemplate) {
        this.msrRabbitTemplate = msrRabbitTemplate;
    }

    @Override
    public void publish(MsrDomainEvent event) {
        String occurredAt = Instant.now().toString();
        String routingKey;
        MsrEventPayload payload;

        switch (event) {
            case MsrCreatedEvent e -> {
                routingKey = "msr.created";
                payload = new MsrEventPayload("MSR_CREATED", e.id(), e.name(), e.surname(), e.email(), e.active(), occurredAt);
            }
            case MsrUpdatedEvent e -> {
                routingKey = "msr.updated";
                payload = new MsrEventPayload("MSR_UPDATED", e.id(), e.name(), e.surname(), e.email(), e.active(), occurredAt);
            }
            case MsrActivatedEvent e -> {
                routingKey = "msr.activated";
                payload = new MsrEventPayload("MSR_ACTIVATED", e.id(), e.name(), e.surname(), e.email(), e.active(), occurredAt);
            }
            case MsrDeactivatedEvent e -> {
                routingKey = "msr.deactivated";
                payload = new MsrEventPayload("MSR_DEACTIVATED", e.id(), e.name(), e.surname(), e.email(), e.active(), occurredAt);
            }
        }

        try {
            msrRabbitTemplate.convertAndSend(EXCHANGE, routingKey, payload);
        } catch (AmqpException ex) {
            log.error("Failed to publish MSR event type={} id={}: {}", payload.eventType(), event, ex.getMessage());
        }
    }
}
