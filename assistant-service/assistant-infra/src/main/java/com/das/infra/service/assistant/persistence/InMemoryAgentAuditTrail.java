package com.das.infra.service.assistant.persistence;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Repository;

import com.das.cleanddd.domain.assistant.audit.AgentAuditEntry;
import com.das.cleanddd.domain.assistant.ports.IAgentAuditTrail;

/**
 * Scaffold audit trail, append-only like the real one will be. To be replaced by an append-only table (as the
 * other services' {@code audit_event}) before the assistant leaves development.
 */
@Repository
public class InMemoryAgentAuditTrail implements IAgentAuditTrail, NonDurableStore {

    private final List<AgentAuditEntry> entries = new CopyOnWriteArrayList<>();

    @Override
    public void append(AgentAuditEntry entry) {
        entries.add(entry);
    }

    @Override
    public List<AgentAuditEntry> forConversation(String conversationId) {
        return entries.stream().filter(e -> e.conversationId().equals(conversationId)).toList();
    }
}
