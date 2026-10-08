package com.das.cleanddd.domain.order.entities;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Plain value holder — not its own aggregate root. Mirrors the
 * Settlement/Invoice child pattern (settlement-domain): lives only inside its
 * parent Order, is never fetched or saved independently.
 *
 * <p>A line starts unpriced (no name or unit price) while its order waits for stock; product-catalog
 * fills both in when it reserves the stock (see {@link #priced}).
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

    public static OrderLine unpriced(ProductId productId, OrderLineQuantity quantity) {
        return new OrderLine(null, productId, null, quantity, null);
    }

    /** A copy of this line (same id) carrying the catalog's name and price snapshot. */
    public OrderLine priced(String productName, OrderLineUnitPrice unitPrice) {
        return new OrderLine(_id, _productId, productName, _quantity, unitPrice);
    }

    public boolean isPriced() {
        return _unitPrice != null;
    }

    /** {@code null} until the line is priced. */
    public BigDecimal lineTotal() {
        if (_unitPrice == null) {
            return null;
        }
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
