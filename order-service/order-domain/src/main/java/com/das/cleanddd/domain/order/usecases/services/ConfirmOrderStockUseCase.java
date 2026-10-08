package com.das.cleanddd.domain.order.usecases.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.das.cleanddd.domain.order.entities.IOrderRepository;
import com.das.cleanddd.domain.order.entities.Order;
import com.das.cleanddd.domain.order.entities.OrderId;
import com.das.cleanddd.domain.order.entities.OrderLineUnitPrice;
import com.das.cleanddd.domain.order.entities.OrderStatus;
import com.das.cleanddd.domain.order.entities.ProductId;
import com.das.cleanddd.domain.order.entities.ReservedLinePrice;
import com.das.cleanddd.domain.order.ports.IOrderEventPublisher;
import com.das.cleanddd.domain.order.usecases.dtos.ConfirmOrderStockInputDTO;
import com.das.cleanddd.domain.order.usecases.dtos.OrderMapper;
import com.das.cleanddd.domain.order.usecases.dtos.OrderOutputDTO;
import com.das.cleanddd.domain.shared.UnitOfWork;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/**
 * Saga step: product-catalog-service reserved every line. Prices the lines and moves the order to
 * PENDING_APPROVAL. Idempotent — an order that has already left AWAITING_STOCK is returned unchanged,
 * so a redelivered answer does nothing.
 */
public final class ConfirmOrderStockUseCase implements UseCase<ConfirmOrderStockInputDTO, OrderOutputDTO> {

    private final IOrderRepository repository;
    private final OrderMapper mapper;
    private final IOrderEventPublisher publisher;
    private final UnitOfWork unitOfWork;

    public ConfirmOrderStockUseCase(IOrderRepository repository, OrderMapper mapper, IOrderEventPublisher publisher,
                                    UnitOfWork unitOfWork) {
        this.repository = repository;
        this.mapper = mapper;
        this.publisher = publisher;
        this.unitOfWork = unitOfWork;
    }

    @Override
    public OrderOutputDTO execute(ConfirmOrderStockInputDTO inputDTO) throws DomainException {
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
                List<ReservedLinePrice> prices = new ArrayList<>();
                for (ConfirmOrderStockInputDTO.ReservedLine line : inputDTO.lines()) {
                    prices.add(new ReservedLinePrice(new ProductId(line.productId()), line.productName(),
                            new OrderLineUnitPrice(line.unitPrice())));
                }
                Order confirmed = order.confirmStock(prices);
                repository.save(confirmed);
                confirmed.pullDomainEvents().forEach(publisher::publish);
                return mapper.outputFromEntity(confirmed);
            } catch (IllegalArgumentException e) {
                throw new DomainException(e.getMessage());
            }
        });
    }
}
