package com.das.infra.service.medicalsalesrep.outbox;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface AggregateVersionJpaRepository extends JpaRepository<AggregateVersionEntity, String> {

    /** Row lock serializes concurrent changes to the same aggregate, so versions never collide. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from AggregateVersionEntity v where v.aggregateKey = :key")
    Optional<AggregateVersionEntity> findForUpdate(@Param("key") String key);
}
