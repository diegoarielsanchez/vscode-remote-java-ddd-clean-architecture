package com.das.infra.service.catalog;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "products")
public class ProductEntity {

    @Id
    private String id;
    private String name;
    private String description;
    private BigDecimal price;
    private String unit;
    private Boolean active;
    /**
     * GS1 GTIN in its 14-digit form; unique (index in the upgrade script). Stock is not a column here: it lives in
     * {@link StockLotEntity} rows, one per batch.
     */
    @Column(length = 14)
    private String gtin;
    /** False for products held without batches (promotional material). Set at creation, never changed. */
    @Column(name = "lot_tracked", nullable = false)
    private Boolean lotTracked = Boolean.TRUE;
    /** Storage and transport conditions from the label; all null when none is on file. */
    @Embedded
    private StorageConditionEmbeddable storageCondition;

    /** Protects the update path (details and GTIN) from concurrent clobbering. */
    @Version
    private Long version;

    // Default constructor
    public ProductEntity() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public String getGtin() {
        return gtin;
    }

    public void setGtin(String gtin) {
        this.gtin = gtin;
    }

    public Boolean getLotTracked() {
        return lotTracked;
    }

    public void setLotTracked(Boolean lotTracked) {
        this.lotTracked = lotTracked;
    }

    public StorageConditionEmbeddable getStorageCondition() {
        return storageCondition;
    }

    public void setStorageCondition(StorageConditionEmbeddable storageCondition) {
        this.storageCondition = storageCondition;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
