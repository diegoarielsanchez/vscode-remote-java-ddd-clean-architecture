package com.das.infra.service.order;

import java.time.Instant;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.order.events.OrderApprovedEvent;
import com.das.cleanddd.domain.order.events.OrderCreatedEvent;
import com.das.cleanddd.domain.order.events.OrderDeliveredEvent;
import com.das.cleanddd.domain.order.events.OrderDomainEvent;
import com.das.cleanddd.domain.order.events.OrderRejectedEvent;
import com.das.cleanddd.domain.order.events.OrderSubmittedForApprovalEvent;
import com.das.cleanddd.domain.order.ports.IOrderEventPublisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Primary
@Service
@Profile("!dev")
public class OrderAmqpEventPublisher implements IOrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderAmqpEventPublisher.class);
    static final String EXCHANGE = "order.events";

    private final RabbitTemplate orderRabbitTemplate;

    public OrderAmqpEventPublisher(RabbitTemplate orderRabbitTemplate) {
        this.orderRabbitTemplate = orderRabbitTemplate;
    }

    @Override
    public void publish(OrderDomainEvent event) {
        String occurredAt = Instant.now().toString();
        String routingKey;
        OrderEventPayload payload;

        switch (event) {
            case OrderCreatedEvent e -> {
                routingKey = "order.created";
                payload = new OrderEventPayload("ORDER_CREATED", e.id(), e.medicalSalesRepId(), e.lineCount(),
                        e.totalAmount(), null, null, occurredAt);
            }
            case OrderSubmittedForApprovalEvent e -> {
                routingKey = "order.submitted-for-approval";
                payload = new OrderEventPayload("ORDER_SUBMITTED_FOR_APPROVAL", e.id(), null, null, null, null, null, occurredAt);
            }
            case OrderApprovedEvent e -> {
                routingKey = "order.approved";
                payload = new OrderEventPayload("ORDER_APPROVED", e.id(), null, null, null, e.approvedBy(), null, occurredAt);
            }
            case OrderRejectedEvent e -> {
                routingKey = "order.rejected";
                payload = new OrderEventPayload("ORDER_REJECTED", e.id(), null, null, null, e.rejectedBy(), e.reason(), occurredAt);
            }
            case OrderDeliveredEvent e -> {
                routingKey = "order.delivered";
                payload = new OrderEventPayload("ORDER_DELIVERED", e.id(), null, null, null, null, null, occurredAt);
            }
        }

        try {
            orderRabbitTemplate.convertAndSend(EXCHANGE, routingKey, payload);
        } catch (AmqpException ex) {
            log.error("Failed to publish Order event type={} id={}: {}", payload.eventType(), payload.id(), ex.getMessage());
        }
    }
}
