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
import com.das.cleanddd.domain.order.entities.OrderLineUnitPrice;
import com.das.cleanddd.domain.order.entities.ProductId;
import com.das.cleanddd.domain.order.ports.IMedicalSalesRepValidator;
import com.das.cleanddd.domain.order.ports.IOrderEventPublisher;
import com.das.cleanddd.domain.order.ports.IProductStockPort;
import com.das.cleanddd.domain.order.ports.IProductStockPort.StockReservationResult;
import com.das.cleanddd.domain.order.usecases.dtos.CreateOrderInputDTO;
import com.das.cleanddd.domain.order.usecases.dtos.OrderLineInputDTO;
import com.das.cleanddd.domain.order.usecases.dtos.OrderMapper;
import com.das.cleanddd.domain.order.usecases.dtos.OrderOutputDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/**
 * The MSR must exist and be active, and every line's stock must be reservable,
 * BEFORE the order is ever created. Reservation happens synchronously, line by
 * line, over REST to product-catalog-service; if any line fails, every
 * already-reserved line is compensated (released) before failing the whole
 * request.
 *
 * This is a best-effort compensating action over synchronous REST calls, NOT a
 * distributed transaction/saga — if the compensating release call itself fails
 * (network blip), stock is left under-released and needs manual reconciliation.
 * That trade-off matches how this codebase already treats cross-service calls
 * (e.g. the AMQP publishers swallow broker errors rather than rolling back).
 */
@Service
public final class CreateOrderUseCase implements UseCase<CreateOrderInputDTO, OrderOutputDTO> {

    @Autowired
    private final IOrderRepository repository;
    @Autowired
    private final OrderMapper mapper;
    private final IOrderEventPublisher publisher;
    private final IMedicalSalesRepValidator medicalSalesRepValidator;
    private final IProductStockPort productStockPort;

    public CreateOrderUseCase(IOrderRepository repository, OrderMapper mapper, IOrderEventPublisher publisher,
                               IMedicalSalesRepValidator medicalSalesRepValidator, IProductStockPort productStockPort) {
        this.repository = repository;
        this.mapper = mapper;
        this.publisher = publisher;
        this.medicalSalesRepValidator = medicalSalesRepValidator;
        this.productStockPort = productStockPort;
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

        Order order;
        try {
            MedicalSalesRepId medicalSalesRepId = new MedicalSalesRepId(inputDTO.medicalSalesRepId());

            List<OrderLine> lines = new ArrayList<>();
            List<String> reservedProductIds = new ArrayList<>();
            List<Integer> reservedQuantities = new ArrayList<>();
            try {
                for (OrderLineInputDTO lineInput : inputDTO.lines()) {
                    StockReservationResult result = productStockPort.reserve(lineInput.productId(), lineInput.quantity());
                    if (!result.reserved()) {
                        throw new DomainException("Insufficient stock for product " + lineInput.productId());
                    }
                    reservedProductIds.add(lineInput.productId());
                    reservedQuantities.add(lineInput.quantity());

                    lines.add(new OrderLine(
                            null,
                            new ProductId(lineInput.productId()),
                            result.productName(),
                            new OrderLineQuantity(lineInput.quantity()),
                            new OrderLineUnitPrice(result.unitPrice())));
                }
            } catch (DomainException | IllegalArgumentException e) {
                // Compensate every reservation that already succeeded before this failure.
                for (int i = 0; i < reservedProductIds.size(); i++) {
                    productStockPort.release(reservedProductIds.get(i), reservedQuantities.get(i));
                }
                throw e;
            }

            order = Order.create(medicalSalesRepId, lines).submitForApproval();
            repository.save(order);
            order.pullDomainEvents().forEach(publisher::publish);
            return mapper.outputFromEntity(order);
        } catch (IllegalArgumentException e) {
            throw new DomainException(e.getMessage());
        }
    }
}
