package com.das.cleanddd.domain.order.usecases.services;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.order.entities.IOrderRepository;
import com.das.cleanddd.domain.order.entities.Order;
import com.das.cleanddd.domain.order.entities.OrderId;
import com.das.cleanddd.domain.order.ports.IOrderEventPublisher;
import com.das.cleanddd.domain.order.usecases.dtos.OrderMapper;
import com.das.cleanddd.domain.order.usecases.dtos.OrderOutputDTO;
import com.das.cleanddd.domain.order.usecases.dtos.RejectOrderInputDTO;
import com.das.cleanddd.domain.shared.UnitOfWork;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/**
 * The rejection and its {@code order.rejected} event commit together in one {@link UnitOfWork}.
 * product-catalog-service consumes that event and releases the order's reserved stock — the
 * compensating step of the stock saga.
 */
@Service
public final class RejectOrderUseCase implements UseCase<RejectOrderInputDTO, OrderOutputDTO> {

    @Autowired
    private final IOrderRepository repository;
    @Autowired
    private final OrderMapper mapper;
    private final IOrderEventPublisher publisher;
    private final UnitOfWork unitOfWork;

    public RejectOrderUseCase(IOrderRepository repository, OrderMapper mapper, IOrderEventPublisher publisher,
                               UnitOfWork unitOfWork) {
        this.repository = repository;
        this.mapper = mapper;
        this.publisher = publisher;
        this.unitOfWork = unitOfWork;
    }

    @Override
    public OrderOutputDTO execute(RejectOrderInputDTO inputDTO) throws DomainException {
        if (inputDTO.orderId() == null) {
            throw new DomainException("Order Id is required.");
        }
        try {
            OrderId id = new OrderId(inputDTO.orderId());
            Order rejected = unitOfWork.execute(() -> {
                Optional<Order> existing = repository.findById(id);
                if (!existing.isPresent()) {
                    throw new DomainException("Order not found.");
                }
                Order changed = existing.get().reject(inputDTO.rejectedBy(), inputDTO.reason());
                repository.save(changed);
                changed.pullDomainEvents().forEach(publisher::publish);
                return changed;
            });
            return mapper.outputFromEntity(rejected);
        } catch (IllegalArgumentException e) {
            throw new DomainException(e.getMessage());
        }
    }
}
