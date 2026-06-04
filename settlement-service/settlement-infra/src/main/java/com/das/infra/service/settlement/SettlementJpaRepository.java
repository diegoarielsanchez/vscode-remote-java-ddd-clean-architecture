package com.das.infra.service.settlement;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementJpaRepository extends JpaRepository<SettlementEntity, String> {
}
