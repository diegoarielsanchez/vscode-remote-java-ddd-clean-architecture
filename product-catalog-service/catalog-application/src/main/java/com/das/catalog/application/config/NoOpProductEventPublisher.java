package com.das.catalog.application.config;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.catalog.events.ProductDomainEvent;
import com.das.cleanddd.domain.catalog.ports.IProductEventPublisher;

/**
 * No-op event publisher for the dev profile (no RabbitMQ broker required).
 */
@Profile("dev")
@Service
public class NoOpProductEventPublisher implements IProductEventPublisher {

    @Override
    public void publish(ProductDomainEvent event) {
        // intentionally empty — events are discarded in dev mode
    }
}
