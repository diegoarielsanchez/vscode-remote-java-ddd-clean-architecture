package com.das.order.application.config;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.order.events.OrderDomainEvent;
import com.das.cleanddd.domain.order.ports.IOrderEventPublisher;

/** No-op event publisher for the dev profile (no RabbitMQ broker required). */
@Profile("dev")
@Service
public class NoOpOrderEventPublisher implements IOrderEventPublisher {

    @Override
    public void publish(OrderDomainEvent event) {
        // intentionally empty — events are discarded in dev mode
    }
}
