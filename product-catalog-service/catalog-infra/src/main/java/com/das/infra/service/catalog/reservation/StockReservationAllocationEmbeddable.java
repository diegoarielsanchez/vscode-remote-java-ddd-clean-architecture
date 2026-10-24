package com.das.infra.service.catalog.reservation;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Units of one reservation line taken from one lot; the batch and expiry are copied for traceability. */
@Embeddable
public class StockReservationAllocationEmbeddable {

    /** Index of the line in {@code stock_reservation_line} this allocation belongs to. */
    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "lot_id", nullable = false, length = 64)
    private String lotId;

    @Column(name = "batch_number", nullable = false, length = 20)
    private String batchNumber;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    protected StockReservationAllocationEmbeddable() {
        // JPA
    }

    public StockReservationAllocationEmbeddable(int lineNo, String lotId, String batchNumber, LocalDate expirationDate,
                                                int quantity) {
        this.lineNo = lineNo;
        this.lotId = lotId;
        this.batchNumber = batchNumber;
        this.expirationDate = expirationDate;
        this.quantity = quantity;
    }

    public int getLineNo() { return lineNo; }
    public String getLotId() { return lotId; }
    public String getBatchNumber() { return batchNumber; }
    public LocalDate getExpirationDate() { return expirationDate; }
    public int getQuantity() { return quantity; }
}
