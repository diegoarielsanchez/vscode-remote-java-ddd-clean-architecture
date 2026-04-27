package com.das.infrapostgresql.service.healthcareprof;

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
}
