package com.das.infra.service.catalog.reservation;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class StockReservationLineEmbeddable {

    @Column(name = "product_id", nullable = false, length = 36)
    private String productId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    /** Snapshot at reservation time; null on a rejected reservation. */
    @Column(name = "product_name", length = 200)
    private String productName;

    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    protected StockReservationLineEmbeddable() {
        // JPA
    }

    public StockReservationLineEmbeddable(String productId, int quantity, String productName, BigDecimal unitPrice) {
        this.productId = productId;
        this.quantity = quantity;
        this.productName = productName;
        this.unitPrice = unitPrice;
    }

    public String getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public String getProductName() { return productName; }
    public BigDecimal getUnitPrice() { return unitPrice; }
}
