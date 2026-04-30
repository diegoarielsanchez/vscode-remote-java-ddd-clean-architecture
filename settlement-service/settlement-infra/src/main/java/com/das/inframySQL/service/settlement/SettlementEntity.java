package com.das.inframySQL.service.settlement;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "settlements")
public class SettlementEntity {

    @Id
    private String id;

    private String description;

    private LocalDate settlementDate;

    private String status;

    private String medicalSalesRepId;

    @OneToMany(mappedBy = "settlement", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<InvoiceEntity> invoices = new ArrayList<>();

    // Default constructor
    public SettlementEntity() {}

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDate getSettlementDate() { return settlementDate; }
    public void setSettlementDate(LocalDate settlementDate) { this.settlementDate = settlementDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMedicalSalesRepId() { return medicalSalesRepId; }
    public void setMedicalSalesRepId(String medicalSalesRepId) { this.medicalSalesRepId = medicalSalesRepId; }

    public List<InvoiceEntity> getInvoices() { return invoices; }
    public void setInvoices(List<InvoiceEntity> invoices) { this.invoices = invoices; }
}
