package com.das.infra.service.catalog.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {

    /**
     * Oldest unpublished events, row-locked. Lock timeout -2 is Hibernate's SKIP LOCKED, so several
     * service replicas can relay concurrently without picking the same rows
     * (PostgreSQL: {@code FOR UPDATE SKIP LOCKED}).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select e from OutboxEventEntity e where e.publishedAt is null order by e.occurredAt asc, e.aggregateVersion asc")
    List<OutboxEventEntity> lockPending(Pageable page);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from OutboxEventEntity e where e.publishedAt is not null and e.publishedAt < :before")
    int deletePublishedBefore(@Param("before") Instant before);

    long countByPublishedAtIsNull();
}
