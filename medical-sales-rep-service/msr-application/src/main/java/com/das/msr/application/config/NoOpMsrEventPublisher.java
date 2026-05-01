package com.das.msr.application.config;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.medicalsalesrep.events.MsrDomainEvent;
import com.das.cleanddd.domain.medicalsalesrep.ports.IMsrEventPublisher;

/**
 * No-op event publisher for the dev profile (no RabbitMQ broker required).
 */
@Profile("dev")
@Service
public class NoOpMsrEventPublisher implements IMsrEventPublisher {

    @Override
    public void publish(MsrDomainEvent event) {
        // intentionally empty — events are discarded in dev mode
    }
}
