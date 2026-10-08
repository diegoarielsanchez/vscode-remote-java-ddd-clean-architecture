package com.das.infra.service.settlement;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Settlement's local copy of a medical sales rep's status — only what the "is this rep active?"
 * rule needs (no names or contact data). Kept current by {@code msr.*} events; rows first seen via
 * the HTTP fallback have no {@code version} (treated as 0).
 */
@Entity
@Table(name = "msr_snapshot")
public class MsrSnapshotEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "event_version")
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MsrSnapshotEntity() {
        // JPA
    }

    public MsrSnapshotEntity(String id) {
        this.id = id;
    }

    public String getId() { return id; }
    public boolean isActive() { return active; }
    public Long getVersion() { return version; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(boolean active, Long version, Instant at) {
        this.active = active;
        this.version = version;
        this.updatedAt = at;
    }
}
