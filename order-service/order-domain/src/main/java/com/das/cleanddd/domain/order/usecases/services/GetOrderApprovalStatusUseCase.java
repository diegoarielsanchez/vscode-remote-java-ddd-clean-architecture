package com.das.cleanddd.domain.order.usecases.services;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;

import com.das.cleanddd.domain.order.entities.IOrderRepository;
import com.das.cleanddd.domain.order.entities.Order;
import com.das.cleanddd.domain.order.entities.OrderId;
import com.das.cleanddd.domain.order.entities.OrderStatus;
import com.das.cleanddd.domain.order.usecases.dtos.OrderApprovalStatusOutputDTO;
import com.das.cleanddd.domain.order.usecases.dtos.OrderIDDto;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/** Backs the internal, unauthenticated {@code GET /{id}/approval-status} endpoint delivery-service calls. */
public class GetOrderApprovalStatusUseCase implements UseCase<OrderIDDto, OrderApprovalStatusOutputDTO> {

    @Autowired
    private final IOrderRepository repository;

    public GetOrderApprovalStatusUseCase(IOrderRepository repository) {
        this.repository = repository;
    }

    @Override
    public OrderApprovalStatusOutputDTO execute(OrderIDDto inputDTO) throws DomainException {
        if (inputDTO.orderId() == null) {
            throw new DomainException("Order Id is required.");
        }
        OrderId id = new OrderId(inputDTO.orderId());
        Optional<Order> order = repository.findById(id);
        if (!order.isPresent()) {
            throw new DomainException("Order not found.");
        }
        OrderStatus status = order.get().status();
        boolean approved = status == OrderStatus.APPROVED || status == OrderStatus.DELIVERED;
        return new OrderApprovalStatusOutputDTO(approved, status.name());
    }
}
