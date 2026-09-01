package com.das.infra.service.healthcareprof;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "health_care_profs")
public class HealthCareProfEntity {

    @Id
    private String id;
    private String name;
    private String surname;
    private String email;
    private Boolean active;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "health_care_prof_specialties", joinColumns = @JoinColumn(name = "health_care_prof_id"))
    @Column(name = "specialty_code_name")
    private List<String> specialties = new ArrayList<>();

    // Each entry is "street|city|state|postalCode|country", state may be empty.
    // Delimiter-encoded rather than an @Embeddable to match the specialties
    // column above — this codebase has no existing @Embeddable precedent.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "health_care_prof_addresses", joinColumns = @JoinColumn(name = "health_care_prof_id"))
    @Column(name = "address_line", length = 600)
    private List<String> addresses = new ArrayList<>();

    public HealthCareProfEntity() {}

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

    public List<String> getSpecialties() { return specialties; }
    public void setSpecialties(List<String> specialties) { this.specialties = specialties; }

    public List<String> getAddresses() { return addresses; }
    public void setAddresses(List<String> addresses) { this.addresses = addresses; }
}
