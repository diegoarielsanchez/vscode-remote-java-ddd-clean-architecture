package com.das.cleanddd.domain.catalog.ports;

import com.das.cleanddd.domain.catalog.events.ProductDomainEvent;

public interface IProductEventPublisher {

    void publish(ProductDomainEvent event);
}
