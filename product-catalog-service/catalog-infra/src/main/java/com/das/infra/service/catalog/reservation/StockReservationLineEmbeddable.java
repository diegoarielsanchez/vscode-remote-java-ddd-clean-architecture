package com.das.infra.service.catalog.reservation;

import java.math.BigDecimal;

import com.das.infra.service.catalog.StorageConditionEmbeddable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;

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

    /** The product's storage conditions at reservation time, which the units travel under; null when none. */
    @Embedded
    private StorageConditionEmbeddable storageCondition;

    protected StockReservationLineEmbeddable() {
        // JPA
    }

    public StockReservationLineEmbeddable(String productId, int quantity, String productName, BigDecimal unitPrice,
                                          StorageConditionEmbeddable storageCondition) {
        this.productId = productId;
        this.quantity = quantity;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.storageCondition = storageCondition;
    }

    public String getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public String getProductName() { return productName; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public StorageConditionEmbeddable getStorageCondition() { return storageCondition; }
}
