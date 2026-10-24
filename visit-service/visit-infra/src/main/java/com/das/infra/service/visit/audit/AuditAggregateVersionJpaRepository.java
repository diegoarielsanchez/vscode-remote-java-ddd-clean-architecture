package com.das.infra.service.visit.audit;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface AuditAggregateVersionJpaRepository extends JpaRepository<AuditAggregateVersionEntity, String> {

    /** Row lock serializes concurrent changes to the same aggregate, so versions never collide. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from AuditAggregateVersionEntity v where v.aggregateKey = :key")
    Optional<AuditAggregateVersionEntity> findForUpdate(@Param("key") String key);
}
