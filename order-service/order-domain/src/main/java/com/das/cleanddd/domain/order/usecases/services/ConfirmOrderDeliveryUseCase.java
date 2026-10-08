package com.das.cleanddd.domain.order.usecases.services;

import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.order.entities.IOrderRepository;
import com.das.cleanddd.domain.order.entities.Order;
import com.das.cleanddd.domain.order.entities.OrderId;
import com.das.cleanddd.domain.order.ports.IOrderEventPublisher;
import com.das.cleanddd.domain.order.usecases.dtos.OrderIDDto;
import com.das.cleanddd.domain.order.usecases.dtos.OrderMapper;
import com.das.cleanddd.domain.order.usecases.dtos.OrderOutputDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.UnitOfWork;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/**
 * Idempotent APPROVED -&gt; DELIVERED transition. Invoked both by delivery-service's
 * RabbitMQ confirmation event and by the manual REST fallback (the AMQP publish is
 * best-effort and can be lost, so a lost message must not strand an order forever).
 */
@Service
public final class ConfirmOrderDeliveryUseCase implements UseCase<OrderIDDto, OrderOutputDTO> {

    @Autowired
    private final IOrderRepository repository;
    @Autowired
    private final OrderMapper mapper;
    private final IOrderEventPublisher publisher;
    private final UnitOfWork unitOfWork;

    public ConfirmOrderDeliveryUseCase(IOrderRepository repository, OrderMapper mapper, IOrderEventPublisher publisher, UnitOfWork unitOfWork) {
        this.repository = repository;
        this.mapper = mapper;
        this.publisher = publisher;
        this.unitOfWork = unitOfWork;
    }

    /**
     * Loading, changing, saving and recording the events happen in one unit of work, so the
     * change and its outbox entries commit (or roll back) together.
     */
    @Override
    public OrderOutputDTO execute(OrderIDDto inputDTO) throws DomainException {
        return unitOfWork.execute(() -> doExecute(inputDTO));
    }

    private OrderOutputDTO doExecute(OrderIDDto inputDTO) throws DomainException {
        if (inputDTO.orderId() == null) {
            throw new DomainException("Order Id is required.");
        }
        try {
            OrderId id = new OrderId(inputDTO.orderId());
            Optional<Order> existing = repository.findById(id);
            if (!existing.isPresent()) {
                throw new DomainException("Order not found.");
            }
            Order delivered = existing.get().markDelivered(Instant.now());
            repository.save(delivered);
            delivered.pullDomainEvents().forEach(publisher::publish);
            return mapper.outputFromEntity(delivered);
        } catch (IllegalArgumentException e) {
            throw new DomainException(e.getMessage());
        }
    }
}
