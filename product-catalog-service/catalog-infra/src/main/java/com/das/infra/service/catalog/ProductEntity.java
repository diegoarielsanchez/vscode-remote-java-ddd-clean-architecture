package com.das.infra.service.catalog;

import java.math.BigDecimal;

import jakarta.persistence.Column;
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
    /**
     * Written on insert only. Afterwards stock changes solely through the atomic queries in
     * {@link ProductJpaRepository} (reserve/add). A product save (activate, deactivate, edit) is
     * built from an earlier read, so writing its stock back would undo any reservation made in
     * between — and {@code @Version} cannot catch that, because the bulk queries don't bump it.
     */
    @Column(updatable = false)
    private Integer stock;
    private Boolean active;

    /** Protects the non-stock update path (name/description/price/unit) from concurrent clobbering. */
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

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
