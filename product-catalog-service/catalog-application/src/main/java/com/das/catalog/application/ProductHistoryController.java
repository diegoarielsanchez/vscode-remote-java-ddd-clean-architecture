package com.das.catalog.application;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.das.infra.service.catalog.outbox.AuditEntryView;
import com.das.infra.service.catalog.outbox.AuditHistory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The audit trail of a product and of an order's stock reservation, for review: every change, oldest first, with who made it, when, why and the previous
 * values. ADMIN only (see SecurityConfig).
 */
@RestController
@CrossOrigin(exposedHeaders = "errors, content-type")
@RequestMapping("api/v1")
@Tag(name = "Audit trail", description = "Read-only history of every change (ALCOA+)")
@SecurityRequirement(name = "bearerAuth")
public class ProductHistoryController {

    private final AuditHistory history;

    public ProductHistoryController(AuditHistory history) {
        this.history = history;
    }

    @GetMapping("/products/{id}/history")
    @Operation(summary = "Audit trail of a product (details, lots, stock), oldest first (admin)")
    public ResponseEntity<List<AuditEntryView>> productHistory(@PathVariable String id) {
        return ResponseEntity.ok(history.of("product", id));
    }

    @GetMapping("/products/reservations/{id}/history")
    @Operation(summary = "Audit trail of an order's stock reservation, oldest first (admin)")
    public ResponseEntity<List<AuditEntryView>> reservationHistory(@PathVariable String id) {
        return ResponseEntity.ok(history.of("reservation", id));
    }
}
