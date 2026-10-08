package com.das.infra.service.catalog.reservation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** One row per order: the stock the catalog holds (or refused to hold) for it. */
@Entity
@Table(name = "stock_reservation")
public class StockReservationEntity {

    @Id
    @Column(name = "order_id", length = 36)
    private String orderId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "reason", length = 500)
    private String reason;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "stock_reservation_line", joinColumns = @JoinColumn(name = "order_id"))
    @OrderColumn(name = "line_no")
    private List<StockReservationLineEmbeddable> lines = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Two consumers racing on the same order: the second commit fails and is retried. */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected StockReservationEntity() {
        // JPA
    }

    public StockReservationEntity(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderId() { return orderId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public List<StockReservationLineEmbeddable> getLines() { return lines; }
    public void setLines(List<StockReservationLineEmbeddable> lines) { this.lines = lines; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public long getVersion() { return version; }
}
