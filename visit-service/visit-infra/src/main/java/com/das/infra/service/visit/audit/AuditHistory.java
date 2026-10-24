package com.das.infra.service.visit.audit;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Reads a record's audit trail, oldest entry first, for review (ALCOA+: available). */
@Service
public class AuditHistory {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AuditEventJpaRepository audit;

    public AuditHistory(AuditEventJpaRepository audit) {
        this.audit = audit;
    }

    /** Empty when the record has no entries (or does not exist). */
    @Transactional(readOnly = true)
    public List<AuditEntryView> of(String aggregateType, String aggregateId) {
        return audit.findByAggregateTypeAndAggregateIdOrderByAggregateVersionAsc(aggregateType, aggregateId).stream()
                .map(AuditHistory::view)
                .toList();
    }

    private static AuditEntryView view(AuditEventEntity e) {
        JsonNode envelope = read(e.getPayload());
        return new AuditEntryView(e.getAggregateVersion(), e.getEventType(), e.getOccurredAt(), e.getActor(),
                e.getCorrelationId(), e.getReason(), read(e.getChanges()), envelope == null ? null : envelope.get("data"));
    }

    private static JsonNode read(String json) {
        if (json == null) {
            return null;
        }
        try {
            return JSON.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unreadable audit entry", ex);
        }
    }
}
