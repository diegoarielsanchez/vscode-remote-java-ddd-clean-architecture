package com.das.cleanddd.domain.assistant.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/**
 * One step of an assistant conversation, kept so a reviewer can see what the rep asked, which tools ran with which
 * input, what the model proposed and what the rep decided (ALCOA+: attributable, contemporaneous, original).
 *
 * <p>Entries are only ever appended. {@code modelId} and {@code promptVersion} record exactly which model and
 * system prompt produced a step, so a change to either is visible in the trail.
 *
 * @param actor         the rep's {@code msr_id}, or {@link #ASSISTANT} for steps the model took
 * @param payloadSha256 SHA-256 of {@code payload}, to detect later alteration
 */
public record AgentAuditEntry(String conversationId, AgentAuditType type, String actor, String modelId,
                              String promptVersion, String payload, String payloadSha256, Instant occurredAt,
                              String draftId) {

    public static final String ASSISTANT = "assistant";

    public AgentAuditEntry {
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(payloadSha256, "payloadSha256");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public static AgentAuditEntry of(String conversationId, AgentAuditType type, String actor, String modelId,
                                     String promptVersion, String payload, Instant occurredAt, String draftId) {
        return new AgentAuditEntry(conversationId, type, actor, modelId, promptVersion, payload, sha256(payload),
                occurredAt, draftId);
    }

    /** Also used to verify a stored entry: recompute it from {@code payload} and compare. */
    public static String sha256(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
