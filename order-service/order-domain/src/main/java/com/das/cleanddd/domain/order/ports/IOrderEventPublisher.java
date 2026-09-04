package com.das.cleanddd.domain.order.ports;

import com.das.cleanddd.domain.order.events.OrderDomainEvent;

public interface IOrderEventPublisher {

    void publish(OrderDomainEvent event);
}
