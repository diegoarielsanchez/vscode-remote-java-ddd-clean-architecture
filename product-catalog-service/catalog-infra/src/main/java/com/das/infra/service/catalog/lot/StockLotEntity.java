package com.das.infra.service.catalog.lot;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

/** One batch of one product. GS1 identifies a lot by GTIN + batch number, hence one row per product and batch. */
@Entity
@Table(name = "stock_lot",
       uniqueConstraints = @UniqueConstraint(name = "ux_stock_lot_product_batch", columnNames = {"product_id", "batch_number"}))
public class StockLotEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "product_id", nullable = false, length = 36)
    private String productId;

    @Column(name = "batch_number", nullable = false, length = 20)
    private String batchNumber;

    /** {@code null} only for stock migrated from before lots existed. */
    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    /**
     * Written on insert only. Afterwards it changes solely through the atomic queries in {@link StockLotJpaRepository}
     * (take/give): a status change is saved from an earlier read, and writing its quantity back would undo any units
     * taken in between.
     */
    @Column(name = "quantity", nullable = false, updatable = false)
    private int quantity;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Version
    @Column(name = "version")
    private Long version;

    protected StockLotEntity() {
        // JPA
    }

    public StockLotEntity(String id, String productId, String batchNumber, LocalDate expirationDate, int quantity,
                          String status, Instant receivedAt) {
        this.id = id;
        this.productId = productId;
        this.batchNumber = batchNumber;
        this.expirationDate = expirationDate;
        this.quantity = quantity;
        this.status = status;
        this.receivedAt = receivedAt;
    }

    public String getId() { return id; }
    public String getProductId() { return productId; }
    public String getBatchNumber() { return batchNumber; }
    public LocalDate getExpirationDate() { return expirationDate; }
    public int getQuantity() { return quantity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getReceivedAt() { return receivedAt; }
    public Long getVersion() { return version; }
}
