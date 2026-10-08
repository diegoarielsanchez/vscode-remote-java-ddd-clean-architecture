package com.das.cleanddd.domain.order.usecases.services;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.order.entities.IOrderRepository;
import com.das.cleanddd.domain.order.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.order.entities.Order;
import com.das.cleanddd.domain.order.entities.OrderLine;
import com.das.cleanddd.domain.order.entities.OrderLineQuantity;
import com.das.cleanddd.domain.order.entities.ProductId;
import com.das.cleanddd.domain.order.ports.IMedicalSalesRepValidator;
import com.das.cleanddd.domain.order.ports.IOrderEventPublisher;
import com.das.cleanddd.domain.order.usecases.dtos.CreateOrderInputDTO;
import com.das.cleanddd.domain.order.usecases.dtos.OrderLineInputDTO;
import com.das.cleanddd.domain.order.usecases.dtos.OrderMapper;
import com.das.cleanddd.domain.order.usecases.dtos.OrderOutputDTO;
import com.das.cleanddd.domain.shared.UnitOfWork;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/**
 * Accepts an order and returns at once, in AWAITING_STOCK. Stock is no longer reserved here: the
 * order and its {@code order.created} event commit together in one {@link UnitOfWork} (the
 * transactional outbox), product-catalog-service reserves every line from that event, and its answer
 * moves the order on ({@link ConfirmOrderStockUseCase} / {@link RejectOrderForStockUseCase}).
 *
 * <p>The MSR check stays synchronous: an order for an unknown or inactive rep is refused up front.
 */
@Service
public final class CreateOrderUseCase implements UseCase<CreateOrderInputDTO, OrderOutputDTO> {

    @Autowired
    private final IOrderRepository repository;
    @Autowired
    private final OrderMapper mapper;
    private final IOrderEventPublisher publisher;
    private final IMedicalSalesRepValidator medicalSalesRepValidator;
    private final UnitOfWork unitOfWork;

    public CreateOrderUseCase(IOrderRepository repository, OrderMapper mapper, IOrderEventPublisher publisher,
                               IMedicalSalesRepValidator medicalSalesRepValidator, UnitOfWork unitOfWork) {
        this.repository = repository;
        this.mapper = mapper;
        this.publisher = publisher;
        this.medicalSalesRepValidator = medicalSalesRepValidator;
        this.unitOfWork = unitOfWork;
    }

    @Override
    public OrderOutputDTO execute(CreateOrderInputDTO inputDTO) throws DomainException {
        if (inputDTO == null) {
            throw new DomainException("Input DTO cannot be null");
        }
        if (inputDTO.medicalSalesRepId() == null || inputDTO.medicalSalesRepId().isBlank()) {
            throw new DomainException("Medical Sales Representative id is required.");
        }
        if (inputDTO.lines() == null || inputDTO.lines().isEmpty()) {
            throw new DomainException("An order must have at least one line.");
        }

        if (!medicalSalesRepValidator.existsAndActive(inputDTO.medicalSalesRepId())) {
            throw new DomainException("Medical Sales Representative not found or not active.");
        }

        try {
            MedicalSalesRepId medicalSalesRepId = new MedicalSalesRepId(inputDTO.medicalSalesRepId());
            List<OrderLine> lines = new ArrayList<>();
            for (OrderLineInputDTO lineInput : inputDTO.lines()) {
                lines.add(OrderLine.unpriced(new ProductId(lineInput.productId()),
                        new OrderLineQuantity(lineInput.quantity())));
            }
            Order created = Order.create(medicalSalesRepId, lines);
            unitOfWork.run(() -> {
                repository.save(created);
                created.pullDomainEvents().forEach(publisher::publish);
            });
            return mapper.outputFromEntity(created);
        } catch (IllegalArgumentException e) {
            throw new DomainException(e.getMessage());
        }
    }
}
