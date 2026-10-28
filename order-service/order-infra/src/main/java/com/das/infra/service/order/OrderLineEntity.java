package com.das.infra.service.order;

import java.math.BigDecimal;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_lines")
public class OrderLineEntity {

    @Id
    private String id;
    private String productId;
    private String productNameSnapshot;
    private Integer quantity;
    private BigDecimal unitPrice;

    /** The conditions the line's units must travel under, from the catalog; null when the product has none. */
    @Embedded
    private StorageConditionEmbeddable storageCondition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    public OrderLineEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getProductNameSnapshot() { return productNameSnapshot; }
    public void setProductNameSnapshot(String productNameSnapshot) { this.productNameSnapshot = productNameSnapshot; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public StorageConditionEmbeddable getStorageCondition() { return storageCondition; }
    public void setStorageCondition(StorageConditionEmbeddable storageCondition) { this.storageCondition = storageCondition; }

    public OrderEntity getOrder() { return order; }
    public void setOrder(OrderEntity order) { this.order = order; }
}
