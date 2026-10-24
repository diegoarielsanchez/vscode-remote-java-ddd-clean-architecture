package com.das.infra.service.visit.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Last audit entry number issued per aggregate ({@code "<type>:<id>"}). */
@Entity
@Table(name = "audit_aggregate_version")
public class AuditAggregateVersionEntity {

    @Id
    @Column(name = "aggregate_key", length = 300)
    private String aggregateKey;

    @Column(name = "last_version", nullable = false)
    private long lastVersion;

    protected AuditAggregateVersionEntity() {
        // JPA
    }

    AuditAggregateVersionEntity(String aggregateKey) {
        this.aggregateKey = aggregateKey;
    }

    long next() {
        return ++lastVersion;
    }
}
