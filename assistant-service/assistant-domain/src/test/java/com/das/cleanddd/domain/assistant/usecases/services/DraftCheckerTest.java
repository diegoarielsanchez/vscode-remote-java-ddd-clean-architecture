package com.das.cleanddd.domain.assistant.usecases.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.das.cleanddd.domain.assistant.entities.CheckOutcome;
import com.das.cleanddd.domain.assistant.entities.DraftCheck;
import com.das.cleanddd.domain.assistant.usecases.dtos.DraftLineInputDTO;

@DisplayName("DraftChecker")
class DraftCheckerTest {

    private final String coldProduct = UUID.randomUUID().toString();
    private final String inactiveProduct = UUID.randomUUID().toString();
    private final String badGtinProduct = UUID.randomUUID().toString();

    private final InMemoryFakes.Catalog catalog = new InMemoryFakes.Catalog()
            .with(InMemoryFakes.product(coldProduct, "Vaccine A", "9506000134352", true, true), 10)
            .with(InMemoryFakes.product(inactiveProduct, "Old B", "4006381333931", false, false), 10)
            .with(InMemoryFakes.product(badGtinProduct, "Odd C", "09506000134353", true, false), 10);
    private final DraftChecker checker = new DraftChecker(catalog);

    private static CheckOutcome outcome(List<DraftCheck> checks, String rule, String productId) {
        return checks.stream().filter(c -> c.rule().equals(rule) && productId.equals(c.productId()))
                .map(DraftCheck::outcome).findFirst().orElseThrow(() -> new AssertionError("no check " + rule));
    }

    @Test
    @DisplayName("should take GTIN and name from the catalog, and warn about a cold-chain product")
    void shouldResolveLineFromCatalog() {
        DraftChecker.Result result = checker.check(List.of(new DraftLineInputDTO(coldProduct, 4)));

        assertEquals(1, result.lines().size());
        assertEquals("09506000134352", result.lines().get(0).gtin().value());
        assertEquals("Vaccine A", result.lines().get(0).name());
        assertEquals(CheckOutcome.PASS, outcome(result.checks(), "stock.available", coldProduct));
        assertEquals(CheckOutcome.WARN, outcome(result.checks(), "storage.cold-chain", coldProduct));
    }

    @Test
    @DisplayName("should fail a line with too little stock but keep it in the draft")
    void shouldFailUnavailableStock() {
        DraftChecker.Result result = checker.check(List.of(new DraftLineInputDTO(coldProduct, 11)));

        assertEquals(1, result.lines().size());
        assertEquals(CheckOutcome.FAIL, outcome(result.checks(), "stock.available", coldProduct));
    }

    @Test
    @DisplayName("should leave out and fail unknown, inactive, bad-GTIN and duplicate products")
    void shouldFailUnresolvableLines() {
        String unknown = UUID.randomUUID().toString();
        DraftChecker.Result result = checker.check(List.of(
                new DraftLineInputDTO(unknown, 1),
                new DraftLineInputDTO(inactiveProduct, 1),
                new DraftLineInputDTO(badGtinProduct, 1),
                new DraftLineInputDTO(coldProduct, 1),
                new DraftLineInputDTO(coldProduct, 2)));

        assertEquals(1, result.lines().size());
        assertEquals(CheckOutcome.FAIL, outcome(result.checks(), "product.exists", unknown));
        assertEquals(CheckOutcome.FAIL, outcome(result.checks(), "product.active", inactiveProduct));
        assertEquals(CheckOutcome.FAIL, outcome(result.checks(), "product.gtin", badGtinProduct));
        assertEquals(CheckOutcome.FAIL, outcome(result.checks(), "line.duplicate", coldProduct));
    }

    @Test
    @DisplayName("should fail a quantity below 1 without asking the catalog")
    void shouldFailBadQuantity() {
        DraftChecker.Result result = checker.check(List.of(new DraftLineInputDTO(coldProduct, 0)));

        assertTrue(result.lines().isEmpty());
        assertEquals(CheckOutcome.FAIL, outcome(result.checks(), "line.quantity", coldProduct));
    }
}
