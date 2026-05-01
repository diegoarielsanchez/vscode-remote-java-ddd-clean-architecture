package com.das.infrapostgresql.service.healthcareprof;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.healthcareprof.events.HcpActivatedEvent;
import com.das.cleanddd.domain.healthcareprof.events.HcpCreatedEvent;
import com.das.cleanddd.domain.healthcareprof.events.HcpDeactivatedEvent;
import com.das.cleanddd.domain.healthcareprof.events.HcpDomainEvent;
import com.das.cleanddd.domain.healthcareprof.events.HcpUpdatedEvent;
import com.das.cleanddd.domain.healthcareprof.ports.IHcpEventPublisher;

@Service
public class HcpAmqpEventPublisher implements IHcpEventPublisher {

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

        hcpRabbitTemplate.convertAndSend(EXCHANGE, routingKey, payload);
    }
}
