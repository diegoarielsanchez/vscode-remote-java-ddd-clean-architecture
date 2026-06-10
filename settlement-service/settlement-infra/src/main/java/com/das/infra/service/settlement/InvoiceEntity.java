package com.das.infra.service.settlement;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "invoices")
public class InvoiceEntity {

    @Id
    private String id;

    private String invoiceNumber;

    private LocalDate issueDate;

    private LocalDate dueDate;

    private BigDecimal amount;

    private String status;

    /** Original file name of the attached digital invoice (nullable). */
    private String invoiceFileFileName;

    /** MIME content type of the attached digital invoice (nullable). */
    private String invoiceFileContentType;

    /** Size in bytes of the attached digital invoice (nullable). */
    private Long invoiceFileSizeInBytes;

    /** SHA-256 hex digest of the attached digital invoice content (nullable). */
    private String invoiceFileHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_id", nullable = false)
    private SettlementEntity settlement;

    // Default constructor
    public InvoiceEntity() {}

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getInvoiceFileFileName() { return invoiceFileFileName; }
    public void setInvoiceFileFileName(String invoiceFileFileName) { this.invoiceFileFileName = invoiceFileFileName; }

    public String getInvoiceFileContentType() { return invoiceFileContentType; }
    public void setInvoiceFileContentType(String invoiceFileContentType) { this.invoiceFileContentType = invoiceFileContentType; }

    public Long getInvoiceFileSizeInBytes() { return invoiceFileSizeInBytes; }
    public void setInvoiceFileSizeInBytes(Long invoiceFileSizeInBytes) { this.invoiceFileSizeInBytes = invoiceFileSizeInBytes; }

    public String getInvoiceFileHash() { return invoiceFileHash; }
    public void setInvoiceFileHash(String invoiceFileHash) { this.invoiceFileHash = invoiceFileHash; }

    public SettlementEntity getSettlement() { return settlement; }
    public void setSettlement(SettlementEntity settlement) { this.settlement = settlement; }
}
