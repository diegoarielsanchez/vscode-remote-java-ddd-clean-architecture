package com.das.inframySQL.service.medicalsalesrep;

import java.time.Instant;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.medicalsalesrep.events.MsrActivatedEvent;
import com.das.cleanddd.domain.medicalsalesrep.events.MsrCreatedEvent;
import com.das.cleanddd.domain.medicalsalesrep.events.MsrDeactivatedEvent;
import com.das.cleanddd.domain.medicalsalesrep.events.MsrDomainEvent;
import com.das.cleanddd.domain.medicalsalesrep.events.MsrUpdatedEvent;
import com.das.cleanddd.domain.medicalsalesrep.ports.IMsrEventPublisher;

import org.springframework.context.annotation.Profile;

@Profile("!dev")
@Service
public class MsrAmqpEventPublisher implements IMsrEventPublisher {

    static final String EXCHANGE = "msr.events";

    @Autowired
    private RabbitTemplate rabbitTemplate;

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

        rabbitTemplate.convertAndSend(EXCHANGE, routingKey, payload);
    }
}
