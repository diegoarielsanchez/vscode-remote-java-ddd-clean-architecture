package com.das.hcp.application.config;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.healthcareprof.events.HcpDomainEvent;
import com.das.cleanddd.domain.healthcareprof.ports.IHcpEventPublisher;

/**
 * No-op event publisher for the dev profile (no RabbitMQ broker required).
 */
@Profile("dev")
@Service
public class NoOpHcpEventPublisher implements IHcpEventPublisher {

    @Override
    public void publish(HcpDomainEvent event) {
        // intentionally empty — events are discarded in dev mode
    }
}
