package com.das.inframySQL.service.visit;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "visit_plans")
public class VisitPlanEntity {

    @Id
    private String id;

    private LocalDateTime visitDateTime;

    @Column(length = 1000)
    private String visitComments;

    private String visitSiteId;

    private String healthCareProfId;

    private String medicalSalesRepId;

    public VisitPlanEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public LocalDateTime getVisitDateTime() { return visitDateTime; }
    public void setVisitDateTime(LocalDateTime visitDateTime) { this.visitDateTime = visitDateTime; }

    public String getVisitComments() { return visitComments; }
    public void setVisitComments(String visitComments) { this.visitComments = visitComments; }

    public String getVisitSiteId() { return visitSiteId; }
    public void setVisitSiteId(String visitSiteId) { this.visitSiteId = visitSiteId; }

    public String getHealthCareProfId() { return healthCareProfId; }
    public void setHealthCareProfId(String healthCareProfId) { this.healthCareProfId = healthCareProfId; }

    public String getMedicalSalesRepId() { return medicalSalesRepId; }
    public void setMedicalSalesRepId(String medicalSalesRepId) { this.medicalSalesRepId = medicalSalesRepId; }
}
