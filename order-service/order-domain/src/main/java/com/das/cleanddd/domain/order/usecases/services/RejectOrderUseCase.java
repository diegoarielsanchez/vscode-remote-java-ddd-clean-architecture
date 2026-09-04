package com.das.cleanddd.domain.order.usecases.services;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.order.entities.IOrderRepository;
import com.das.cleanddd.domain.order.entities.Order;
import com.das.cleanddd.domain.order.entities.OrderId;
import com.das.cleanddd.domain.order.entities.OrderLine;
import com.das.cleanddd.domain.order.ports.IOrderEventPublisher;
import com.das.cleanddd.domain.order.ports.IProductStockPort;
import com.das.cleanddd.domain.order.usecases.dtos.OrderMapper;
import com.das.cleanddd.domain.order.usecases.dtos.OrderOutputDTO;
import com.das.cleanddd.domain.order.usecases.dtos.RejectOrderInputDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/** Rejecting an order returns every line's reserved stock to product-catalog-service — the other half of the reservation lifecycle. */
@Service
public final class RejectOrderUseCase implements UseCase<RejectOrderInputDTO, OrderOutputDTO> {

    @Autowired
    private final IOrderRepository repository;
    @Autowired
    private final OrderMapper mapper;
    private final IOrderEventPublisher publisher;
    private final IProductStockPort productStockPort;

    public RejectOrderUseCase(IOrderRepository repository, OrderMapper mapper, IOrderEventPublisher publisher,
                               IProductStockPort productStockPort) {
        this.repository = repository;
        this.mapper = mapper;
        this.publisher = publisher;
        this.productStockPort = productStockPort;
    }

    @Override
    public OrderOutputDTO execute(RejectOrderInputDTO inputDTO) throws DomainException {
        if (inputDTO.orderId() == null) {
            throw new DomainException("Order Id is required.");
        }
        try {
            OrderId id = new OrderId(inputDTO.orderId());
            Optional<Order> existing = repository.findById(id);
            if (!existing.isPresent()) {
                throw new DomainException("Order not found.");
            }
            Order rejected = existing.get().reject(inputDTO.rejectedBy(), inputDTO.reason());
            repository.save(rejected);
            for (OrderLine line : rejected.lines()) {
                productStockPort.release(line.productId().value(), line.quantity().value());
            }
            rejected.pullDomainEvents().forEach(publisher::publish);
            return mapper.outputFromEntity(rejected);
        } catch (IllegalArgumentException e) {
            throw new DomainException(e.getMessage());
        }
    }
}
