package com.das.cleanddd.domain.visit.entities;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.das.cleanddd.domain.shared.Identifier;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;

/**
 * A product, and how many units of it, handed out (visit) or to bring (visit plan).
 */
public class VisitItem {

    public static final int MAX_QUANTITY = 1000;
    public static final int MAX_ITEMS = 50;

    private final UUID _visitItemId = UUID.randomUUID();
    private final Identifier _productId;
    private final int _quantity;

    public VisitItem(Identifier productId, int quantity) {
        if (productId == null) {
            throw new IllegalArgumentException("Visit item product is required.");
        }
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw new IllegalArgumentException("Visit item quantity must be between 1 and " + MAX_QUANTITY + ".");
        }
        this._productId = productId;
        this._quantity = quantity;
    }

    /**
     * The items as one line per product — repeated products are added up — within {@value #MAX_ITEMS} lines.
     */
    public static List<VisitItem> combine(List<VisitItem> items) throws BusinessValidationException {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> quantityByProduct = new LinkedHashMap<>();
        Map<String, Identifier> productById = new LinkedHashMap<>();
        for (VisitItem item : items) {
            String productId = item.productId().value();
            quantityByProduct.merge(productId, item.quantity(), Integer::sum);
            productById.putIfAbsent(productId, item.productId());
        }
        if (quantityByProduct.size() > MAX_ITEMS) {
            throw new BusinessValidationException("A visit can list at most " + MAX_ITEMS + " products.");
        }
        List<VisitItem> combined = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : quantityByProduct.entrySet()) {
            if (entry.getValue() > MAX_QUANTITY) {
                throw new BusinessValidationException(
                        "Visit item quantity must be between 1 and " + MAX_QUANTITY + ".");
            }
            combined.add(new VisitItem(productById.get(entry.getKey()), entry.getValue()));
        }
        return combined;
    }

    public int quantity() {
        return this._quantity;
    }

    public Identifier productId() {
        return this._productId;
    }

    public UUID visitItemId() {
        return this._visitItemId;
    }

    /** Items as the audit trail shows them: {@code "<productId> x <quantity>"}. */
    public static List<String> describe(List<VisitItem> items) {
        return items == null ? List.of()
                : items.stream().map(i -> i._productId.value() + " x " + i._quantity).toList();
    }
}
