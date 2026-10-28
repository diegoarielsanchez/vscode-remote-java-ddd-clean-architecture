package com.das.infra.service.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    private String id;
    private String medicalSalesRepId;
    private String status;
    private String approvedBy;
    private String rejectedBy;
    private String rejectionReason;
    private Instant createdAt;
    private Instant approvedAt;
    private Instant rejectedAt;
    private Instant deliveredAt;

    // The receiver's transport report (GDP); all null until delivery, or when none was given.
    @Column(name = "transport_conditions_maintained")
    private Boolean transportConditionsMaintained;
    @Column(name = "transport_min_temp_c", precision = 4, scale = 1)
    private BigDecimal transportMinTemperatureC;
    @Column(name = "transport_max_temp_c", precision = 4, scale = 1)
    private BigDecimal transportMaxTemperatureC;
    @Column(name = "transport_max_humidity")
    private Integer transportMaxHumidityPercent;
    @Column(name = "transport_light_exposed")
    private Boolean transportLightExposed;
    @Column(name = "transport_note", length = 500)
    private String transportNote;
    /** One breach per line, as worked out at delivery. */
    @Column(name = "transport_breaches", length = 4000)
    private String transportBreaches;

    // Quality's decision on a quarantined delivery; null otherwise.
    @Column(name = "delivery_decided_by")
    private String deliveryDecidedBy;
    @Column(name = "delivery_decision_reason", length = 500)
    private String deliveryDecisionReason;
    @Column(name = "delivery_decided_at")
    private Instant deliveryDecidedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderLineEntity> lines = new ArrayList<>();

    public OrderEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getMedicalSalesRepId() { return medicalSalesRepId; }
    public void setMedicalSalesRepId(String medicalSalesRepId) { this.medicalSalesRepId = medicalSalesRepId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public String getRejectedBy() { return rejectedBy; }
    public void setRejectedBy(String rejectedBy) { this.rejectedBy = rejectedBy; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }

    public Instant getRejectedAt() { return rejectedAt; }
    public void setRejectedAt(Instant rejectedAt) { this.rejectedAt = rejectedAt; }

    public Instant getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Instant deliveredAt) { this.deliveredAt = deliveredAt; }

    public Boolean getTransportConditionsMaintained() { return transportConditionsMaintained; }
    public void setTransportConditionsMaintained(Boolean value) { this.transportConditionsMaintained = value; }
    public BigDecimal getTransportMinTemperatureC() { return transportMinTemperatureC; }
    public void setTransportMinTemperatureC(BigDecimal value) { this.transportMinTemperatureC = value; }
    public BigDecimal getTransportMaxTemperatureC() { return transportMaxTemperatureC; }
    public void setTransportMaxTemperatureC(BigDecimal value) { this.transportMaxTemperatureC = value; }
    public Integer getTransportMaxHumidityPercent() { return transportMaxHumidityPercent; }
    public void setTransportMaxHumidityPercent(Integer value) { this.transportMaxHumidityPercent = value; }
    public Boolean getTransportLightExposed() { return transportLightExposed; }
    public void setTransportLightExposed(Boolean value) { this.transportLightExposed = value; }
    public String getTransportNote() { return transportNote; }
    public void setTransportNote(String value) { this.transportNote = value; }
    public String getTransportBreaches() { return transportBreaches; }
    public void setTransportBreaches(String value) { this.transportBreaches = value; }

    public String getDeliveryDecidedBy() { return deliveryDecidedBy; }
    public void setDeliveryDecidedBy(String value) { this.deliveryDecidedBy = value; }
    public String getDeliveryDecisionReason() { return deliveryDecisionReason; }
    public void setDeliveryDecisionReason(String value) { this.deliveryDecisionReason = value; }
    public Instant getDeliveryDecidedAt() { return deliveryDecidedAt; }
    public void setDeliveryDecidedAt(Instant value) { this.deliveryDecidedAt = value; }

    public List<OrderLineEntity> getLines() { return lines; }
    public void setLines(List<OrderLineEntity> lines) { this.lines = lines; }
}
