package com.das.inframySQL.service.visit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Option B — local read-model snapshot of HealthCareProf data.
 * Populated by {@link HcpSnapshotUpdater} from AMQP events and
 * seeded on startup by {@link SnapshotBootstrapService}.
 */
@Entity
@Table(name = "hcp_snapshot")
public class HcpSnapshotEntity {

    @Id
    private String id;

    private String name;
    private String surname;
    private String email;
    private Boolean active;

    /** Comma-separated specialty codes; may be null. */
    @Column(length = 2000)
    private String specialties;

    public HcpSnapshotEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSurname() { return surname; }
    public void setSurname(String surname) { this.surname = surname; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public String getSpecialties() { return specialties; }
    public void setSpecialties(String specialties) { this.specialties = specialties; }
}
