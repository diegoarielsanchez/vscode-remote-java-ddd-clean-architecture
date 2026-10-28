package com.das.infra.service.order;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.das.cleanddd.domain.order.entities.DeliveryCheck;
import com.das.cleanddd.domain.order.entities.DeliveryDecision;
import com.das.cleanddd.domain.shared.storage.ConditionReadings;
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

/**
 * Reads are transactional: {@code OrderEntity.lines} is lazy and open-in-view is off, so the
 * entity must be mapped to the domain model while its session is still open.
 */
@Primary
@Service
public class SQLOrderRepository implements IOrderRepository {

    /** Breaches are sentences without line breaks, so they are stored one per line. */
    private static final String BREACH_SEPARATOR = "\n";

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
    @Transactional(readOnly = true)
    public Optional<Order> findById(OrderId id) {
        String idValue = id.value();
        if (idValue == null) {
            return Optional.empty();
        }
        return jpaRepository.findById(idValue).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
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
                    entity.getDeliveredAt(),
                    deliveryCheck(entity),
                    entity.getDeliveryDecidedBy() == null ? null : new DeliveryDecision(entity.getDeliveryDecidedBy(),
                            entity.getDeliveryDecisionReason(), entity.getDeliveryDecidedAt()));
        } catch (BusinessValidationException e) {
            // A persisted row has already passed these invariants once; a violation here
            // means the stored data itself is corrupt — an infrastructure failure, not a
            // domain error the caller can recover from.
            throw new IllegalStateException("Corrupt order record " + entity.getId() + ": " + e.getMessage(), e);
        }
    }

    private static DeliveryCheck deliveryCheck(OrderEntity e) {
        if (e.getTransportConditionsMaintained() == null) {
            return null;
        }
        boolean read = e.getTransportMinTemperatureC() != null || e.getTransportMaxTemperatureC() != null
                || e.getTransportMaxHumidityPercent() != null || e.getTransportLightExposed() != null;
        return new DeliveryCheck(e.getTransportConditionsMaintained(),
                read ? new ConditionReadings(e.getTransportMinTemperatureC(), e.getTransportMaxTemperatureC(),
                        e.getTransportMaxHumidityPercent(), e.getTransportLightExposed()) : null,
                e.getTransportNote(),
                e.getTransportBreaches() == null ? List.of() : List.of(e.getTransportBreaches().split(BREACH_SEPARATOR)));
    }

    private OrderLine lineToDomain(OrderLineEntity le) {
        try {
            return new OrderLine(
                    new OrderLineId(le.getId()),
                    new ProductId(le.getProductId()),
                    le.getProductNameSnapshot(),
                    new OrderLineQuantity(le.getQuantity()),
                    le.getUnitPrice() == null ? null : new OrderLineUnitPrice(le.getUnitPrice()),
                    StorageConditionEmbeddable.toDomain(le.getStorageCondition()));
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
        DeliveryCheck check = domain.deliveryCheck();
        if (check != null) {
            ConditionReadings r = check.readings();
            entity.setTransportConditionsMaintained(check.conditionsMaintained());
            entity.setTransportMinTemperatureC(r == null ? null : r.minTemperatureC());
            entity.setTransportMaxTemperatureC(r == null ? null : r.maxTemperatureC());
            entity.setTransportMaxHumidityPercent(r == null ? null : r.maxHumidityPercent());
            entity.setTransportLightExposed(r == null ? null : r.lightExposed());
            entity.setTransportNote(check.note());
            entity.setTransportBreaches(check.breaches().isEmpty() ? null : String.join(BREACH_SEPARATOR, check.breaches()));
        }
        DeliveryDecision decision = domain.deliveryDecision();
        if (decision != null) {
            entity.setDeliveryDecidedBy(decision.decidedBy());
            entity.setDeliveryDecisionReason(decision.reason());
            entity.setDeliveryDecidedAt(decision.decidedAt());
        }

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
        le.setUnitPrice(line.unitPrice() == null ? null : line.unitPrice().value());
        le.setStorageCondition(StorageConditionEmbeddable.of(line.storageCondition()));
        le.setOrder(parent);
        return le;
    }
}
