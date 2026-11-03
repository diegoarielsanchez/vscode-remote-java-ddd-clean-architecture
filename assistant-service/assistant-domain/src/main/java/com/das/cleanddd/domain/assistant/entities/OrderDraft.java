package com.das.cleanddd.domain.assistant.entities;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;

/**
 * An order the assistant proposed for a Medical Sales Rep, waiting for the rep's decision.
 *
 * <p>A draft is not an order: nothing is reserved or placed until the rep confirms it, and only a rep can confirm
 * (the assistant has no way to). It belongs to the rep it was drafted for, and only that rep may see or decide on
 * it. A draft whose checks contain a {@link CheckOutcome#FAIL} cannot be confirmed.
 *
 * <p>An order carries no HCP: {@code contextHcpId} only records which HCP, usually from a visit, the draft was
 * prepared for, so the audit trail can show why it was proposed.
 */
public final class OrderDraft {

    public static final int MAX_LINES = 20;
    public static final int MAX_RATIONALE_LENGTH = 500;

    private final OrderDraftId id;
    private final String medicalSalesRepId;
    private final String conversationId;
    private final List<DraftLine> lines;
    private List<DraftCheck> checks;
    private final String contextHcpId;
    private final String rationale;
    private final Instant createdAt;
    private final Instant expiresAt;
    private DraftStatus status;
    private String orderId;
    private Instant decidedAt;

    private OrderDraft(OrderDraftId id, String medicalSalesRepId, String conversationId, List<DraftLine> lines,
                       List<DraftCheck> checks, String contextHcpId, String rationale, Instant createdAt,
                       Instant expiresAt, DraftStatus status, String orderId, Instant decidedAt) {
        this.id = id;
        this.medicalSalesRepId = medicalSalesRepId;
        this.conversationId = conversationId;
        this.lines = List.copyOf(lines);
        this.checks = List.copyOf(checks);
        this.contextHcpId = contextHcpId;
        this.rationale = rationale;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.status = status;
        this.orderId = orderId;
        this.decidedAt = decidedAt;
    }

    /**
     * A new draft, valid for {@code ttl}. Lines that could not be resolved in the catalog are left out and show up
     * as failed checks instead, so such a draft can be shown but not confirmed.
     */
    public static OrderDraft propose(String medicalSalesRepId, String conversationId, List<DraftLine> lines,
                                     List<DraftCheck> checks, String contextHcpId, String rationale,
                                     Instant now, Duration ttl) throws BusinessValidationException {
        if (medicalSalesRepId == null || medicalSalesRepId.isBlank()) {
            throw new BusinessValidationException("A draft needs the Medical Sales Rep it is for.");
        }
        if (conversationId == null || conversationId.isBlank()) {
            throw new BusinessValidationException("A draft needs the conversation it came from.");
        }
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(checks, "checks");
        if (lines.size() > MAX_LINES) {
            throw new BusinessValidationException("A draft can have at most " + MAX_LINES + " lines.");
        }
        if (lines.isEmpty() && checks.stream().noneMatch(DraftCheck::failed)) {
            throw new BusinessValidationException("A draft needs at least one line.");
        }
        if (rationale != null && rationale.length() > MAX_RATIONALE_LENGTH) {
            throw new BusinessValidationException(
                    "The rationale can be at most " + MAX_RATIONALE_LENGTH + " characters.");
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new BusinessValidationException("A draft needs a positive time to live.");
        }
        return new OrderDraft(OrderDraftId.random(), medicalSalesRepId, conversationId, lines, checks, contextHcpId,
                rationale, now, now.plus(ttl), DraftStatus.DRAFTED, null, null);
    }

    /** Rebuilds a stored draft; no rule is re-applied. */
    public static OrderDraft restore(OrderDraftId id, String medicalSalesRepId, String conversationId,
                                     List<DraftLine> lines, List<DraftCheck> checks, String contextHcpId,
                                     String rationale, Instant createdAt, Instant expiresAt, DraftStatus status,
                                     String orderId, Instant decidedAt) {
        return new OrderDraft(id, medicalSalesRepId, conversationId, lines, checks, contextHcpId, rationale,
                createdAt, expiresAt, status, orderId, decidedAt);
    }

    public boolean belongsTo(String medicalSalesRepId) {
        return this.medicalSalesRepId.equals(medicalSalesRepId);
    }

    /** {@link DraftStatus#EXPIRED} once its time is up and nobody decided, whatever is stored. */
    public DraftStatus statusAt(Instant now) {
        return status == DraftStatus.DRAFTED && !now.isBefore(expiresAt) ? DraftStatus.EXPIRED : status;
    }

    public boolean hasFailedCheck() {
        return checks.stream().anyMatch(DraftCheck::failed);
    }

    /**
     * Checks run again just before confirming: stock or a product's status may have changed since the draft was
     * proposed.
     */
    public void recheck(List<DraftCheck> currentChecks) {
        this.checks = List.copyOf(Objects.requireNonNull(currentChecks, "currentChecks"));
    }

    /** Called once the rep confirmed and the order was accepted by order-service. */
    public void confirm(String placedOrderId, Instant now) throws BusinessValidationException {
        ensureOpen(now);
        if (hasFailedCheck()) {
            throw new BusinessValidationException("A draft with a failed check cannot be confirmed.");
        }
        if (placedOrderId == null || placedOrderId.isBlank()) {
            throw new BusinessValidationException("A confirmed draft needs the order it became.");
        }
        this.status = DraftStatus.CONFIRMED;
        this.orderId = placedOrderId;
        this.decidedAt = now;
    }

    public void discard(Instant now) throws BusinessValidationException {
        ensureOpen(now);
        this.status = DraftStatus.DISCARDED;
        this.decidedAt = now;
    }

    /** Whether the rep may still confirm it, before the checks are run again. */
    public void ensureOpen(Instant now) throws BusinessValidationException {
        DraftStatus current = statusAt(now);
        if (current != DraftStatus.DRAFTED) {
            throw new BusinessValidationException("The draft is " + current + " and can no longer be decided on.");
        }
    }

    public OrderDraftId id() {
        return id;
    }

    public String medicalSalesRepId() {
        return medicalSalesRepId;
    }

    public String conversationId() {
        return conversationId;
    }

    public List<DraftLine> lines() {
        return lines;
    }

    public List<DraftCheck> checks() {
        return checks;
    }

    public String contextHcpId() {
        return contextHcpId;
    }

    public String rationale() {
        return rationale;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public String orderId() {
        return orderId;
    }

    public Instant decidedAt() {
        return decidedAt;
    }
}
