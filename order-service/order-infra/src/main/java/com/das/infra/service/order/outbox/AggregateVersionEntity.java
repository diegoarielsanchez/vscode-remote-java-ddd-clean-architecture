package com.das.infra.service.order.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Last integration-event version issued per aggregate ({@code "<type>:<id>"}). */
@Entity
@Table(name = "outbox_aggregate_version")
public class AggregateVersionEntity {

    @Id
    @Column(name = "aggregate_key", length = 120)
    private String aggregateKey;

    @Column(name = "last_version", nullable = false)
    private long lastVersion;

    protected AggregateVersionEntity() {
        // JPA
    }

    AggregateVersionEntity(String aggregateKey) {
        this.aggregateKey = aggregateKey;
    }

    long next() {
        return ++lastVersion;
    }
}
