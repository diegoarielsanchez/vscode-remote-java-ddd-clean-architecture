package com.das.infra.service.healthcareprof;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.healthcareprof.events.HcpActivatedEvent;
import com.das.cleanddd.domain.healthcareprof.events.HcpCreatedEvent;
import com.das.cleanddd.domain.healthcareprof.events.HcpDeactivatedEvent;
import com.das.cleanddd.domain.healthcareprof.events.HcpDomainEvent;
import com.das.cleanddd.domain.healthcareprof.events.HcpUpdatedEvent;
import com.das.cleanddd.domain.healthcareprof.ports.IHcpEventPublisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Primary
@Service
@Profile("!dev")
public class HcpAmqpEventPublisher implements IHcpEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(HcpAmqpEventPublisher.class);
    static final String EXCHANGE = "hcp.events";

    @Autowired
    private final RabbitTemplate hcpRabbitTemplate;

    public HcpAmqpEventPublisher(RabbitTemplate hcpRabbitTemplate) {
        this.hcpRabbitTemplate = hcpRabbitTemplate;
    }

    @Override
    public void publish(HcpDomainEvent event) {
        String routingKey;
        HcpEventPayload payload;

        switch (event) {
            case HcpCreatedEvent e -> {
                routingKey = "hcp.created";
                payload = HcpEventPayload.of("HCP_CREATED", e.id(), e.name(), e.surname(), e.email(), e.active());
            }
            case HcpUpdatedEvent e -> {
                routingKey = "hcp.updated";
                payload = HcpEventPayload.of("HCP_UPDATED", e.id(), e.name(), e.surname(), e.email(), e.active());
            }
            case HcpActivatedEvent e -> {
                routingKey = "hcp.activated";
                payload = HcpEventPayload.of("HCP_ACTIVATED", e.id(), null, null, null, e.active());
            }
            case HcpDeactivatedEvent e -> {
                routingKey = "hcp.deactivated";
                payload = HcpEventPayload.of("HCP_DEACTIVATED", e.id(), null, null, null, e.active());
            }
        }

        try {
            hcpRabbitTemplate.convertAndSend(EXCHANGE, routingKey, payload);
        } catch (AmqpException ex) {
            log.error("Failed to publish HCP event type={} id={}: {}", payload.eventType(), id(event), ex.getMessage());
        }
    }

    private String id(HcpDomainEvent event) {
        return switch (event) {
            case HcpCreatedEvent e -> e.id();
            case HcpUpdatedEvent e -> e.id();
            case HcpActivatedEvent e -> e.id();
            case HcpDeactivatedEvent e -> e.id();
        };
    }
}
