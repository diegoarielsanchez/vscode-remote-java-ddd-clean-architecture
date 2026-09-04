package com.das.infra.service.catalog;

import java.time.Instant;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.catalog.events.ProductActivatedEvent;
import com.das.cleanddd.domain.catalog.events.ProductCreatedEvent;
import com.das.cleanddd.domain.catalog.events.ProductDeactivatedEvent;
import com.das.cleanddd.domain.catalog.events.ProductDomainEvent;
import com.das.cleanddd.domain.catalog.events.ProductRestockedEvent;
import com.das.cleanddd.domain.catalog.events.ProductStockReleasedEvent;
import com.das.cleanddd.domain.catalog.events.ProductStockReservedEvent;
import com.das.cleanddd.domain.catalog.events.ProductUpdatedEvent;
import com.das.cleanddd.domain.catalog.ports.IProductEventPublisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Primary
@Service
@Profile("!dev")
public class ProductAmqpEventPublisher implements IProductEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ProductAmqpEventPublisher.class);
    static final String EXCHANGE = "catalog.events";

    private final RabbitTemplate catalogRabbitTemplate;

    public ProductAmqpEventPublisher(RabbitTemplate catalogRabbitTemplate) {
        this.catalogRabbitTemplate = catalogRabbitTemplate;
    }

    @Override
    public void publish(ProductDomainEvent event) {
        String occurredAt = Instant.now().toString();
        String routingKey;
        ProductEventPayload payload;

        switch (event) {
            case ProductCreatedEvent e -> {
                routingKey = "catalog.product.created";
                payload = new ProductEventPayload("PRODUCT_CREATED", e.id(), e.name(), e.description(), e.price(),
                        e.unit(), e.active(), null, null, occurredAt);
            }
            case ProductUpdatedEvent e -> {
                routingKey = "catalog.product.updated";
                payload = new ProductEventPayload("PRODUCT_UPDATED", e.id(), e.name(), e.description(), e.price(),
                        e.unit(), e.active(), null, null, occurredAt);
            }
            case ProductActivatedEvent e -> {
                routingKey = "catalog.product.activated";
                payload = new ProductEventPayload("PRODUCT_ACTIVATED", e.id(), null, null, null, null, e.active(),
                        null, null, occurredAt);
            }
            case ProductDeactivatedEvent e -> {
                routingKey = "catalog.product.deactivated";
                payload = new ProductEventPayload("PRODUCT_DEACTIVATED", e.id(), null, null, null, null, e.active(),
                        null, null, occurredAt);
            }
            case ProductStockReservedEvent e -> {
                routingKey = "catalog.product.stock-reserved";
                payload = new ProductEventPayload("PRODUCT_STOCK_RESERVED", e.id(), null, null, null, null, null,
                        e.quantityReserved(), e.remainingStock(), occurredAt);
            }
            case ProductStockReleasedEvent e -> {
                routingKey = "catalog.product.stock-released";
                payload = new ProductEventPayload("PRODUCT_STOCK_RELEASED", e.id(), null, null, null, null, null,
                        e.quantityReleased(), e.remainingStock(), occurredAt);
            }
            case ProductRestockedEvent e -> {
                routingKey = "catalog.product.restocked";
                payload = new ProductEventPayload("PRODUCT_RESTOCKED", e.id(), null, null, null, null, null,
                        e.quantityAdded(), e.remainingStock(), occurredAt);
            }
        }

        try {
            catalogRabbitTemplate.convertAndSend(EXCHANGE, routingKey, payload);
        } catch (AmqpException ex) {
            log.error("Failed to publish Product event type={} id={}: {}", payload.eventType(), payload.id(), ex.getMessage());
        }
    }
}
