package com.das.infra.service.order.outbox;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.Repository;

/**
 * Append-only access to {@link AuditEventEntity}: insert and read, nothing else. Extends {@link Repository}
 * rather than {@code JpaRepository} so no update or delete method exists.
 */
public interface AuditEventJpaRepository extends Repository<AuditEventEntity, UUID> {

    AuditEventEntity save(AuditEventEntity event);

    /** A record's full history, oldest first. */
    List<AuditEventEntity> findByAggregateTypeAndAggregateIdOrderByAggregateVersionAsc(String aggregateType,
                                                                                       String aggregateId);

    long count();
}
