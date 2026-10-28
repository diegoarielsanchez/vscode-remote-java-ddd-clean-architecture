package com.das.cleanddd.domain.order.usecases.services;

import java.time.Instant;
import java.util.Optional;

import com.das.cleanddd.domain.order.entities.IOrderRepository;
import com.das.cleanddd.domain.order.entities.Order;
import com.das.cleanddd.domain.order.entities.OrderId;
import com.das.cleanddd.domain.order.ports.IOrderEventPublisher;
import com.das.cleanddd.domain.order.usecases.dtos.DecideDeliveryInputDTO;
import com.das.cleanddd.domain.order.usecases.dtos.OrderMapper;
import com.das.cleanddd.domain.order.usecases.dtos.OrderOutputDTO;
import com.das.cleanddd.domain.shared.UnitOfWork;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/**
 * Quality's decision on a delivery quarantined for a transport break: release it (the goods may be used, and the
 * catalog's reservation is fulfilled) or reject it (the goods await destruction). The decision, who made it and
 * why commit with their events in one {@link UnitOfWork}.
 */
public final class DecideDeliveryUseCase implements UseCase<DecideDeliveryInputDTO, OrderOutputDTO> {

    private final IOrderRepository repository;
    private final OrderMapper mapper;
    private final IOrderEventPublisher publisher;
    private final UnitOfWork unitOfWork;
    private final boolean release;

    /** {@code release}: true releases the delivery, false rejects it. */
    public DecideDeliveryUseCase(IOrderRepository repository, OrderMapper mapper, IOrderEventPublisher publisher,
                                 UnitOfWork unitOfWork, boolean release) {
        this.repository = repository;
        this.mapper = mapper;
        this.publisher = publisher;
        this.unitOfWork = unitOfWork;
        this.release = release;
    }

    @Override
    public OrderOutputDTO execute(DecideDeliveryInputDTO inputDTO) throws DomainException {
        if (inputDTO == null || inputDTO.orderId() == null) {
            throw new DomainException("Order Id is required.");
        }
        return unitOfWork.execute(() -> {
            try {
                Optional<Order> existing = repository.findById(new OrderId(inputDTO.orderId()));
                if (existing.isEmpty()) {
                    throw new DomainException("Order not found.");
                }
                Instant now = Instant.now();
                Order decided = release
                        ? existing.get().releaseDelivery(inputDTO.decidedBy(), inputDTO.reason(), now)
                        : existing.get().rejectDelivery(inputDTO.decidedBy(), inputDTO.reason(), now);
                repository.save(decided);
                decided.pullDomainEvents().forEach(publisher::publish);
                return mapper.outputFromEntity(decided);
            } catch (IllegalArgumentException e) {
                throw new DomainException(e.getMessage());
            }
        });
    }
}
