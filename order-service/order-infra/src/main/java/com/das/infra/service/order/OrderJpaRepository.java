package com.das.infra.service.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, String> {

    Page<OrderEntity> findByMedicalSalesRepId(String medicalSalesRepId, Pageable pageable);
}
