package com.das.infra.service.order;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.order.entities.IOrderRepository;
import com.das.cleanddd.domain.order.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.order.entities.Order;
import com.das.cleanddd.domain.order.entities.OrderId;
import com.das.cleanddd.domain.order.entities.OrderLine;
import com.das.cleanddd.domain.order.entities.OrderLineId;
import com.das.cleanddd.domain.order.entities.OrderLineQuantity;
import com.das.cleanddd.domain.order.entities.OrderLineUnitPrice;
import com.das.cleanddd.domain.order.entities.OrderStatus;
import com.das.cleanddd.domain.order.entities.ProductId;
import com.das.cleanddd.domain.shared.criteria.Criteria;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;

@Primary
@Service
public final class SQLOrderRepository implements IOrderRepository {

    @Autowired
    private OrderJpaRepository jpaRepository;

    @Override
    public void save(Order order) {
        OrderEntity entity = toEntity(order);
        if (entity != null) {
            jpaRepository.save(entity);
        }
    }

    @Override
    public Optional<Order> findById(OrderId id) {
        String idValue = id.value();
        if (idValue == null) {
            return Optional.empty();
        }
        return jpaRepository.findById(idValue).map(this::toDomain);
    }

    @Override
    public List<Order> findByMedicalSalesRepId(MedicalSalesRepId medicalSalesRepId, int page, int pageSize) {
        return jpaRepository.findByMedicalSalesRepId(medicalSalesRepId.value(), PageRequest.of(page - 1, pageSize))
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Order> matching(Criteria criteria) {
        return null;
    }

    @Override
    public List<Order> searchAll() {
        return jpaRepository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    private Order toDomain(OrderEntity entity) {
        List<OrderLine> lines = entity.getLines().stream()
                .map(this::lineToDomain)
                .collect(Collectors.toList());
        try {
            return new Order(
                    new OrderId(entity.getId()),
                    new MedicalSalesRepId(entity.getMedicalSalesRepId()),
                    lines,
                    OrderStatus.valueOf(entity.getStatus()),
                    entity.getApprovedBy(),
                    entity.getRejectedBy(),
                    entity.getRejectionReason(),
                    entity.getCreatedAt(),
                    entity.getApprovedAt(),
                    entity.getRejectedAt(),
                    entity.getDeliveredAt());
        } catch (BusinessValidationException e) {
            // A persisted row has already passed these invariants once; a violation here
            // means the stored data itself is corrupt — an infrastructure failure, not a
            // domain error the caller can recover from.
            throw new IllegalStateException("Corrupt order record " + entity.getId() + ": " + e.getMessage(), e);
        }
    }

    private OrderLine lineToDomain(OrderLineEntity le) {
        try {
            return new OrderLine(
                    new OrderLineId(le.getId()),
                    new ProductId(le.getProductId()),
                    le.getProductNameSnapshot(),
                    new OrderLineQuantity(le.getQuantity()),
                    new OrderLineUnitPrice(le.getUnitPrice()));
        } catch (BusinessValidationException e) {
            throw new IllegalStateException("Corrupt order line record " + le.getId() + ": " + e.getMessage(), e);
        }
    }

    private OrderEntity toEntity(Order domain) {
        OrderEntity entity = new OrderEntity();
        entity.setId(domain.id().value());
        entity.setMedicalSalesRepId(domain.medicalSalesRepId().value());
        entity.setStatus(domain.status().name());
        entity.setApprovedBy(domain.approvedBy());
        entity.setRejectedBy(domain.rejectedBy());
        entity.setRejectionReason(domain.rejectionReason());
        entity.setCreatedAt(domain.createdAt());
        entity.setApprovedAt(domain.approvedAt());
        entity.setRejectedAt(domain.rejectedAt());
        entity.setDeliveredAt(domain.deliveredAt());

        List<OrderLineEntity> lineEntities = domain.lines().stream()
                .map(line -> lineToEntity(line, entity))
                .collect(Collectors.toList());
        entity.setLines(lineEntities);

        return entity;
    }

    private OrderLineEntity lineToEntity(OrderLine line, OrderEntity parent) {
        OrderLineEntity le = new OrderLineEntity();
        le.setId(line.id().value());
        le.setProductId(line.productId().value());
        le.setProductNameSnapshot(line.productNameSnapshot());
        le.setQuantity(line.quantity().value());
        le.setUnitPrice(line.unitPrice().value());
        le.setOrder(parent);
        return le;
    }
}
