package com.das.cleanddd.domain.assistant.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;

@DisplayName("OrderDraft")
class OrderDraftTest {

    private static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");
    private static final Duration TTL = Duration.ofHours(2);
    private final String msrId = UUID.randomUUID().toString();
    private final String conversationId = UUID.randomUUID().toString();
    private final DraftLine line = new DraftLine(UUID.randomUUID().toString(), new Gtin("9506000134352"), "Product A", 5);

    private OrderDraft propose(List<DraftCheck> checks) throws BusinessValidationException {
        return OrderDraft.propose(msrId, conversationId, List.of(line), checks, null, "Asked at the visit", NOW, TTL);
    }

    @Test
    @DisplayName("should start DRAFTED for its rep and expire after its time to live")
    void shouldStartDrafted() throws BusinessValidationException {
        OrderDraft draft = propose(List.of());

        assertEquals(DraftStatus.DRAFTED, draft.statusAt(NOW));
        assertTrue(draft.belongsTo(msrId));
        assertFalse(draft.belongsTo(UUID.randomUUID().toString()));
        assertEquals(DraftStatus.EXPIRED, draft.statusAt(NOW.plus(TTL)));
    }

    @Test
    @DisplayName("should become CONFIRMED with the order id when confirmed in time")
    void shouldConfirm() throws BusinessValidationException {
        OrderDraft draft = propose(List.of(DraftCheck.warn("storage.cold-chain", line.productId(), "Cold chain")));
        String orderId = UUID.randomUUID().toString();

        draft.confirm(orderId, NOW.plusSeconds(60));

        assertEquals(DraftStatus.CONFIRMED, draft.statusAt(NOW.plus(TTL)));
        assertEquals(orderId, draft.orderId());
    }

    @Test
    @DisplayName("should refuse to confirm a draft with a failed check")
    void shouldNotConfirmFailedDraft() throws BusinessValidationException {
        OrderDraft draft = propose(List.of(DraftCheck.fail("stock.available", line.productId(), "No stock")));

        assertThrows(BusinessValidationException.class, () -> draft.confirm(UUID.randomUUID().toString(), NOW));
        assertEquals(DraftStatus.DRAFTED, draft.statusAt(NOW));
    }

    @Test
    @DisplayName("should refuse any decision once expired, confirmed or discarded")
    void shouldNotDecideTwice() throws BusinessValidationException {
        OrderDraft expired = propose(List.of());
        assertThrows(BusinessValidationException.class, () -> expired.discard(NOW.plus(TTL)));

        OrderDraft discarded = propose(List.of());
        discarded.discard(NOW);
        assertThrows(BusinessValidationException.class, () -> discarded.confirm(UUID.randomUUID().toString(), NOW));
    }

    @Test
    @DisplayName("should reject more than 20 lines, a long rationale, or no rep")
    void shouldEnforceLimits() {
        List<DraftLine> tooMany = Collections.nCopies(OrderDraft.MAX_LINES + 1, line);
        assertThrows(BusinessValidationException.class,
                () -> OrderDraft.propose(msrId, conversationId, tooMany, List.of(), null, null, NOW, TTL));
        assertThrows(BusinessValidationException.class,
                () -> OrderDraft.propose(msrId, conversationId, List.of(line), List.of(), null, "x".repeat(501), NOW, TTL));
        assertThrows(BusinessValidationException.class,
                () -> OrderDraft.propose(" ", conversationId, List.of(line), List.of(), null, null, NOW, TTL));
    }
}
