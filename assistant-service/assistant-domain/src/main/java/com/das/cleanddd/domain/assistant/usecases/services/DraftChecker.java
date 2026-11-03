package com.das.cleanddd.domain.assistant.usecases.services;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.das.cleanddd.domain.assistant.entities.DraftCheck;
import com.das.cleanddd.domain.assistant.entities.DraftLine;
import com.das.cleanddd.domain.assistant.entities.Gtin;
import com.das.cleanddd.domain.assistant.ports.ICatalogPort;
import com.das.cleanddd.domain.assistant.ports.ProductView;
import com.das.cleanddd.domain.assistant.usecases.dtos.DraftLineInputDTO;

/**
 * The rules a draft is checked against: plain code, the same every time, independent of the model.
 *
 * <p>Each proposed line is resolved in the catalog. A line that resolves becomes a {@link DraftLine} carrying the
 * catalog's GTIN and name; one that does not is left out and reported as a failed check, so the rep sees what was
 * dropped and why. The catalog being unreachable fails the line (fail closed).
 */
public class DraftChecker {

    private final ICatalogPort catalog;

    public DraftChecker(ICatalogPort catalog) {
        this.catalog = catalog;
    }

    public record Result(List<DraftLine> lines, List<DraftCheck> checks) {}

    public Result check(List<DraftLineInputDTO> proposed) {
        List<DraftLine> lines = new ArrayList<>();
        List<DraftCheck> checks = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (DraftLineInputDTO line : proposed) {
            String productId = line.productId();
            if (productId == null || productId.isBlank()) {
                checks.add(DraftCheck.fail("line.product", null, "A line has no product."));
                continue;
            }
            if (!seen.add(productId)) {
                checks.add(DraftCheck.fail("line.duplicate", productId,
                        "The product is listed twice; put the whole quantity on one line."));
                continue;
            }
            if (line.quantity() < 1) {
                checks.add(DraftCheck.fail("line.quantity", productId, "The quantity must be at least 1."));
                continue;
            }
            Optional<ProductView> found = catalog.findProduct(productId);
            if (found.isEmpty()) {
                checks.add(DraftCheck.fail("product.exists", productId,
                        "The product is not in the catalog, or the catalog could not be reached."));
                continue;
            }
            ProductView product = found.get();
            if (!product.active()) {
                checks.add(DraftCheck.fail("product.active", productId, product.name() + " is not active."));
                continue;
            }
            Gtin gtin;
            try {
                gtin = new Gtin(product.gtin());
            } catch (IllegalArgumentException e) {
                checks.add(DraftCheck.fail("product.gtin", productId,
                        product.name() + " has no valid GS1 GTIN in the catalog."));
                continue;
            }
            lines.add(new DraftLine(productId, gtin, product.name(), line.quantity()));
            checks.add(DraftCheck.pass("product.gtin", productId, "GTIN " + gtin.value() + " is valid."));
            checks.add(catalog.isAvailable(productId, line.quantity())
                    ? DraftCheck.pass("stock.available", productId, line.quantity() + " can be supplied.")
                    : DraftCheck.fail("stock.available", productId,
                            "Not enough deliverable stock for " + line.quantity() + "."));
            if (product.coldChain()) {
                checks.add(DraftCheck.warn("storage.cold-chain", productId, product.name()
                        + " is a cold-chain product: make sure the delivery can keep its storage conditions."));
            }
            if (product.protectFromLight()) {
                checks.add(DraftCheck.warn("storage.light", productId,
                        product.name() + " must be protected from light."));
            }
            if (product.lotTracked()) {
                checks.add(DraftCheck.pass("product.lot-tracked", productId,
                        "Lots are assigned first-expiry-first-out when stock is reserved."));
            }
        }
        if (lines.isEmpty() && checks.isEmpty()) {
            checks.add(DraftCheck.fail("draft.lines", null, "The draft has no lines."));
        }
        return new Result(lines, checks);
    }

    /** The checks for lines already in a draft, run again before confirming. */
    public Result recheck(List<DraftLine> lines) {
        return check(lines.stream().map(l -> new DraftLineInputDTO(l.productId(), l.quantity())).toList());
    }
}
