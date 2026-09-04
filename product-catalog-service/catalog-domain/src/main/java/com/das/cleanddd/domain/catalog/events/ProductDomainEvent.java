package com.das.cleanddd.domain.catalog.events;

public sealed interface ProductDomainEvent
        permits ProductCreatedEvent, ProductUpdatedEvent, ProductActivatedEvent, ProductDeactivatedEvent,
        ProductStockReservedEvent, ProductStockReleasedEvent, ProductRestockedEvent {
}
