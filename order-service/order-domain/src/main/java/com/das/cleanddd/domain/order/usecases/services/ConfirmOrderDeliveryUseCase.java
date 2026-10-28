package com.das.cleanddd.domain.order.usecases.services;

import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.order.entities.DeliveryCheck;
import com.das.cleanddd.domain.order.entities.IOrderRepository;
import com.das.cleanddd.domain.order.entities.Order;
import com.das.cleanddd.domain.order.entities.OrderId;
import com.das.cleanddd.domain.order.ports.IOrderEventPublisher;
import com.das.cleanddd.domain.order.usecases.dtos.ConfirmOrderDeliveryInputDTO;
import com.das.cleanddd.domain.order.usecases.dtos.OrderMapper;
import com.das.cleanddd.domain.order.usecases.dtos.OrderOutputDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.UnitOfWork;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import com.das.cleanddd.domain.shared.storage.ConditionReadings;

/**
 * Idempotent APPROVED -&gt; DELIVERED transition. Invoked both by delivery-service's
 * RabbitMQ confirmation event and by the manual REST fallback (the AMQP publish is
 * best-effort and can be lost, so a lost message must not strand an order forever).
 * A Medical Sales Rep may only confirm their own orders (OWASP A01): see
 * {@link ConfirmOrderDeliveryInputDTO#requiredMedicalSalesRepId()}.
 *
 * <p>GDP: when the order has products with storage conditions, the receiver reports whether transport kept them
 * (with the shipment logger's readings when there is one). A break quarantines the delivery instead of completing
 * it; see {@link com.das.cleanddd.domain.order.entities.Order#markDelivered(Instant, DeliveryCheck)}.
 */
@Service
public final class ConfirmOrderDeliveryUseCase implements UseCase<ConfirmOrderDeliveryInputDTO, OrderOutputDTO> {

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
    public OrderOutputDTO execute(ConfirmOrderDeliveryInputDTO inputDTO) throws DomainException {
        return unitOfWork.execute(() -> doExecute(inputDTO));
    }

    private OrderOutputDTO doExecute(ConfirmOrderDeliveryInputDTO inputDTO) throws DomainException {
        if (inputDTO.orderId() == null) {
            throw new DomainException("Order Id is required.");
        }
        try {
            OrderId id = new OrderId(inputDTO.orderId());
            Optional<Order> existing = repository.findById(id);
            // Someone else's order answers exactly like a missing one, so a rep cannot probe
            // which order ids exist.
            if (!existing.isPresent() || !belongsToRequiredRep(existing.get(), inputDTO.requiredMedicalSalesRepId())) {
                throw new DomainException("Order not found.");
            }
            Order delivered = existing.get().markDelivered(Instant.now(), check(inputDTO.transport()));
            repository.save(delivered);
            delivered.pullDomainEvents().forEach(publisher::publish);
            return mapper.outputFromEntity(delivered);
        } catch (IllegalArgumentException e) {
            throw new DomainException(e.getMessage());
        }
    }

    private static DeliveryCheck check(ConfirmOrderDeliveryInputDTO.TransportInputDTO transport) throws DomainException {
        if (transport == null) {
            return null;
        }
        if (transport.conditionsMaintained() == null) {
            throw new DomainException("transport.conditionsMaintained is required: say whether transport kept the"
                    + " products' storage conditions.");
        }
        ConditionReadings readings = transport.hasReadings()
                ? new ConditionReadings(transport.minTemperatureC(), transport.maxTemperatureC(),
                        transport.maxHumidityPercent(), transport.lightExposed())
                : null;
        return DeliveryCheck.reported(transport.conditionsMaintained(), readings, transport.note());
    }

    private static boolean belongsToRequiredRep(Order order, String requiredMedicalSalesRepId) {
        return requiredMedicalSalesRepId == null
                || requiredMedicalSalesRepId.equals(order.medicalSalesRepId().value());
    }
}
