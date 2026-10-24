package com.das.hcp.application;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.das.infra.service.healthcareprof.outbox.AuditEntryView;
import com.das.infra.service.healthcareprof.outbox.AuditHistory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The audit trail of a Healthcare Professional, for review: every change, oldest first, with who made it, when, why and the previous
 * values. ADMIN only (see SecurityConfig).
 */
@RestController
@CrossOrigin(exposedHeaders = "errors, content-type")
@RequestMapping("api/v1")
@Tag(name = "Audit trail", description = "Read-only history of every change (ALCOA+)")
@SecurityRequirement(name = "bearerAuth")
public class HealthCareProfHistoryController {

    private final AuditHistory history;

    public HealthCareProfHistoryController(AuditHistory history) {
        this.history = history;
    }

    @GetMapping("/healthcareprof/{id}/history")
    @Operation(summary = "Audit trail of a Healthcare Professional, oldest first (admin)")
    public ResponseEntity<List<AuditEntryView>> healthCareProfHistory(@PathVariable String id) {
        return ResponseEntity.ok(history.of("hcp", id));
    }
}
