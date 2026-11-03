package com.das.cleanddd.domain.assistant.ports;

import java.util.List;

import com.das.cleanddd.domain.assistant.audit.AgentAuditEntry;

/** Append-only record of every assistant step. Nothing in it is ever changed or deleted. */
public interface IAgentAuditTrail {

    void append(AgentAuditEntry entry);

    /** The conversation's entries, oldest first. */
    List<AgentAuditEntry> forConversation(String conversationId);
}
