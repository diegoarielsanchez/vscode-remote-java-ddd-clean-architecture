package com.das.order.application;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.das.infra.service.order.outbox.AuditEntryView;
import com.das.infra.service.order.outbox.AuditHistory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The audit trail of an order, for review: every change, oldest first, with who made it, when, why and the previous
 * values. ADMIN only (see SecurityConfig).
 */
@RestController
@CrossOrigin(exposedHeaders = "errors, content-type")
@RequestMapping("api/v1")
@Tag(name = "Audit trail", description = "Read-only history of every change (ALCOA+)")
@SecurityRequirement(name = "bearerAuth")
public class OrderHistoryController {

    private final AuditHistory history;

    public OrderHistoryController(AuditHistory history) {
        this.history = history;
    }

    @GetMapping("/orders/{id}/history")
    @Operation(summary = "Audit trail of an order, oldest first (admin)")
    public ResponseEntity<List<AuditEntryView>> orderHistory(@PathVariable String id) {
        return ResponseEntity.ok(history.of("order", id));
    }
}
