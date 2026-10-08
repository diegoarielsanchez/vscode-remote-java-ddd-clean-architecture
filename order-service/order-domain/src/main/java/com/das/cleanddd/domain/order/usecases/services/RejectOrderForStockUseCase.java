package com.das.cleanddd.domain.order.usecases.services;

import java.util.Optional;

import com.das.cleanddd.domain.order.entities.IOrderRepository;
import com.das.cleanddd.domain.order.entities.Order;
import com.das.cleanddd.domain.order.entities.OrderId;
import com.das.cleanddd.domain.order.entities.OrderStatus;
import com.das.cleanddd.domain.order.ports.IOrderEventPublisher;
import com.das.cleanddd.domain.order.usecases.dtos.OrderMapper;
import com.das.cleanddd.domain.order.usecases.dtos.OrderOutputDTO;
import com.das.cleanddd.domain.order.usecases.dtos.RejectOrderForStockInputDTO;
import com.das.cleanddd.domain.shared.UnitOfWork;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/**
 * Saga step: product-catalog-service could not reserve every line (and holds nothing back). Moves
 * the order to STOCK_REJECTED. Idempotent — an order that has already left AWAITING_STOCK is
 * returned unchanged.
 */
public final class RejectOrderForStockUseCase implements UseCase<RejectOrderForStockInputDTO, OrderOutputDTO> {

    private final IOrderRepository repository;
    private final OrderMapper mapper;
    private final IOrderEventPublisher publisher;
    private final UnitOfWork unitOfWork;

    public RejectOrderForStockUseCase(IOrderRepository repository, OrderMapper mapper, IOrderEventPublisher publisher,
                                      UnitOfWork unitOfWork) {
        this.repository = repository;
        this.mapper = mapper;
        this.publisher = publisher;
        this.unitOfWork = unitOfWork;
    }

    @Override
    public OrderOutputDTO execute(RejectOrderForStockInputDTO inputDTO) throws DomainException {
        if (inputDTO == null || inputDTO.orderId() == null) {
            throw new DomainException("Order Id is required.");
        }
        return unitOfWork.execute(() -> {
            try {
                Optional<Order> existing = repository.findById(new OrderId(inputDTO.orderId()));
                if (existing.isEmpty()) {
                    throw new DomainException("Order not found.");
                }
                Order order = existing.get();
                if (order.status() != OrderStatus.AWAITING_STOCK) {
                    return mapper.outputFromEntity(order);
                }
                Order rejected = order.rejectForStock(inputDTO.reason());
                repository.save(rejected);
                rejected.pullDomainEvents().forEach(publisher::publish);
                return mapper.outputFromEntity(rejected);
            } catch (IllegalArgumentException e) {
                throw new DomainException(e.getMessage());
            }
        });
    }
}
