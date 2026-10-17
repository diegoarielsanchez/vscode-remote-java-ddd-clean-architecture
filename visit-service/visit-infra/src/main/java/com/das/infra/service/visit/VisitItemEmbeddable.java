package com.das.infra.service.visit;

import java.util.ArrayList;
import java.util.List;

import com.das.cleanddd.domain.shared.Identifier;
import com.das.cleanddd.domain.visit.entities.VisitItem;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** One product line of a visit or visit plan, stored in its item collection table. */
@Embeddable
public class VisitItemEmbeddable {

    @Column(name = "product_id", nullable = false, length = 36)
    private String productId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    protected VisitItemEmbeddable() {}

    public VisitItemEmbeddable(String productId, int quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    public String getProductId() { return productId; }
    public int getQuantity() { return quantity; }

    static List<VisitItemEmbeddable> fromDomain(List<VisitItem> items) {
        List<VisitItemEmbeddable> rows = new ArrayList<>();
        for (VisitItem item : items) {
            rows.add(new VisitItemEmbeddable(item.productId().value(), item.quantity()));
        }
        return rows;
    }

    static List<VisitItem> toDomain(List<VisitItemEmbeddable> rows) {
        List<VisitItem> items = new ArrayList<>();
        if (rows != null) {
            for (VisitItemEmbeddable row : rows) {
                items.add(new VisitItem(new Identifier(row.getProductId()) {}, row.getQuantity()));
            }
        }
        return items;
    }
}
