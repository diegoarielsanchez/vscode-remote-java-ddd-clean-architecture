package com.das.infra.service.order.events;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventEntity, String> {
}
