package com.das.cleanddd.domain.order.entities;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Plain value holder — not its own aggregate root. Mirrors the
 * Settlement/Invoice child pattern (settlement-domain): lives only inside its
 * parent Order, is never fetched or saved independently.
 */
public final class OrderLine {

    private final OrderLineId _id;
    private final ProductId _productId;
    private final String _productNameSnapshot;
    private final OrderLineQuantity _quantity;
    private final OrderLineUnitPrice _unitPrice;

    public OrderLine(OrderLineId id, ProductId productId, String productNameSnapshot,
                      OrderLineQuantity quantity, OrderLineUnitPrice unitPrice) {
        this._id = id == null ? OrderLineId.random() : id;
        this._productId = productId;
        this._productNameSnapshot = productNameSnapshot;
        this._quantity = quantity;
        this._unitPrice = unitPrice;
    }

    public OrderLineId id() {
        return _id;
    }

    public ProductId productId() {
        return _productId;
    }

    public String productNameSnapshot() {
        return _productNameSnapshot;
    }

    public OrderLineQuantity quantity() {
        return _quantity;
    }

    public OrderLineUnitPrice unitPrice() {
        return _unitPrice;
    }

    public BigDecimal lineTotal() {
        return _unitPrice.value().multiply(BigDecimal.valueOf(_quantity.value()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrderLine other)) return false;
        return Objects.equals(_id, other._id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_id);
    }
}
