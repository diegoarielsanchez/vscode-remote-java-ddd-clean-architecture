package com.das.infra.service.assistant.persistence;

/**
 * Marks a store that keeps its data in memory only, lost on restart. The assistant refuses to start enabled under
 * the {@code prod} profile while any store is one of these: drafts and the audit trail must be durable before the
 * assistant is used for real (GxP data integrity).
 */
public interface NonDurableStore {
}
