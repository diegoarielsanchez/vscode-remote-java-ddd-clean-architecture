package com.das.inframySQL.service.visit;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Option B — local read-model snapshot of MedicalSalesRep data.
 * Populated by {@link MsrSnapshotUpdater} from AMQP events and
 * seeded on startup by {@link SnapshotBootstrapService}.
 */
@Entity
@Table(name = "msr_snapshot")
public class MsrSnapshotEntity {

    @Id
    private String id;

    private String name;
    private String surname;
    private String email;
    private Boolean active;

    public MsrSnapshotEntity() {}

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
}
