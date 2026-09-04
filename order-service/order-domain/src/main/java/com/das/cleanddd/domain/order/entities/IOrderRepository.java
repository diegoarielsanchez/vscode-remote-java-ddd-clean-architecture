package com.das.cleanddd.domain.order.entities;

import java.util.List;
import java.util.Optional;

import com.das.cleanddd.domain.shared.criteria.Criteria;

public interface IOrderRepository {

    void save(Order order);

    Optional<Order> findById(OrderId id);

    List<Order> findByMedicalSalesRepId(MedicalSalesRepId medicalSalesRepId, int page, int pageSize);

    List<Order> matching(Criteria criteria);

    List<Order> searchAll();
}
