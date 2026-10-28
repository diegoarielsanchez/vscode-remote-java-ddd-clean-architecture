package com.das.cleanddd.domain.order.usecases.dtos;

import java.util.List;

import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.order.entities.Order;
import com.das.cleanddd.domain.order.entities.OrderLine;

@Service
public class OrderMapper {

    public OrderOutputDTO outputFromEntity(Order order) {
        List<OrderLineOutputDTO> lines = order.lines().stream()
                .map(this::lineOutputFromEntity)
                .toList();
        return new OrderOutputDTO(
            order.id().value(),
            order.medicalSalesRepId().value(),
            order.status().name(),
            lines,
            order.totalAmount(),
            order.approvedBy(),
            order.rejectedBy(),
            order.rejectionReason(),
            order.createdAt() == null ? null : order.createdAt().toString(),
            order.approvedAt() == null ? null : order.approvedAt().toString(),
            order.rejectedAt() == null ? null : order.rejectedAt().toString(),
            order.deliveredAt() == null ? null : order.deliveredAt().toString(),
            order.requiresTransportCheck(),
            DeliveryCheckOutputDTO.from(order.deliveryCheck(), order.deliveryDecision())
        );
    }

    private OrderLineOutputDTO lineOutputFromEntity(OrderLine line) {
        return new OrderLineOutputDTO(
            line.productId().value(),
            line.productNameSnapshot(),
            line.quantity().value(),
            line.unitPrice() == null ? null : line.unitPrice().value(),
            line.lineTotal(),
            StorageConditionOutputDTO.from(line.storageCondition())
        );
    }

    public List<OrderOutputDTO> outputFromEntityList(List<Order> orders) {
        return orders.stream()
            .map(this::outputFromEntity)
            .toList();
    }
}
