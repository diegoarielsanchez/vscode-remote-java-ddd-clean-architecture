package com.das.cleanddd.domain.assistant.usecases.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.das.cleanddd.domain.assistant.audit.AgentAuditType;
import com.das.cleanddd.domain.assistant.ports.AssistantModelVersion;
import com.das.cleanddd.domain.assistant.ports.IOrderPort;
import com.das.cleanddd.domain.assistant.usecases.dtos.DraftDecisionInputDTO;
import com.das.cleanddd.domain.assistant.usecases.dtos.DraftLineInputDTO;
import com.das.cleanddd.domain.assistant.usecases.dtos.OrderDraftMapper;
import com.das.cleanddd.domain.assistant.usecases.dtos.OrderDraftOutputDTO;
import com.das.cleanddd.domain.assistant.usecases.dtos.ProposeOrderDraftInputDTO;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

@DisplayName("Propose, confirm and discard an order draft")
class DraftUseCasesTest {

    private static final AssistantModelVersion VERSION = new AssistantModelVersion("claude-opus-5-5", "system-v1:test");
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T10:00:00Z"), ZoneOffset.UTC);

    private final String msrId = UUID.randomUUID().toString();
    private final String otherMsrId = UUID.randomUUID().toString();
    private final String conversationId = UUID.randomUUID().toString();
    private final String productId = UUID.randomUUID().toString();

    private final InMemoryFakes.Drafts drafts = new InMemoryFakes.Drafts();
    private final InMemoryFakes.AuditTrail audit = new InMemoryFakes.AuditTrail();
    private final InMemoryFakes.Catalog catalog = new InMemoryFakes.Catalog()
            .with(InMemoryFakes.product(productId, "Product A", "9506000134352", true, false), 10);
    private final IOrderPort orders = mock(IOrderPort.class);

    private final DraftChecker checker = new DraftChecker(catalog);
    private final OrderDraftMapper mapper = new OrderDraftMapper();
    private final ProposeOrderDraftUseCase propose = new ProposeOrderDraftUseCase(drafts, checker, audit, VERSION,
            mapper, clock, Duration.ofHours(2));
    private final ConfirmDraftUseCase confirm = new ConfirmDraftUseCase(drafts, checker, orders, audit, VERSION,
            mapper, clock);
    private final DiscardDraftUseCase discard = new DiscardDraftUseCase(drafts, audit, VERSION, mapper, clock);
    private final GetDraftUseCase get = new GetDraftUseCase(drafts, mapper, clock);

    private OrderDraftOutputDTO proposeLine(int quantity) throws DomainException {
        return propose.execute(new ProposeOrderDraftInputDTO(msrId, conversationId,
                List.of(new DraftLineInputDTO(productId, quantity)), null, "Asked for at the visit"));
    }

    @Test
    @DisplayName("proposing should store a confirmable draft and audit it, without placing any order")
    void proposeShouldOnlyDraft() throws DomainException {
        OrderDraftOutputDTO draft = proposeLine(3);

        assertEquals("DRAFTED", draft.status());
        assertTrue(draft.confirmable());
        assertEquals("09506000134352", draft.lines().get(0).gtin());
        assertEquals(AgentAuditType.PROPOSAL, audit.entries.get(0).type());
        assertEquals("claude-opus-5-5", audit.entries.get(0).modelId());
        verify(orders, never()).placeOrder(anyList());
    }

    @Test
    @DisplayName("confirming should re-check, place the order as the rep, and record the human decision")
    void confirmShouldPlaceOrder() throws DomainException {
        OrderDraftOutputDTO draft = proposeLine(3);
        String orderId = UUID.randomUUID().toString();
        when(orders.placeOrder(anyList())).thenReturn(orderId);

        OrderDraftOutputDTO confirmed = confirm.execute(new DraftDecisionInputDTO(msrId, draft.id()));

        assertEquals("CONFIRMED", confirmed.status());
        assertEquals(orderId, confirmed.orderId());
        assertEquals(AgentAuditType.HUMAN_DECISION, audit.entries.get(audit.entries.size() - 1).type());
        assertEquals(msrId, audit.entries.get(audit.entries.size() - 1).actor());
    }

    @Test
    @DisplayName("confirming should refuse, without ordering, when stock ran out since the draft was proposed")
    void confirmShouldRecheck() throws DomainException {
        OrderDraftOutputDTO draft = proposeLine(3);
        catalog.stock.put(productId, 1);

        assertThrows(BusinessValidationException.class,
                () -> confirm.execute(new DraftDecisionInputDTO(msrId, draft.id())));
        verify(orders, never()).placeOrder(anyList());
        assertFalse(get.execute(new DraftDecisionInputDTO(msrId, draft.id())).confirmable());
    }

    @Test
    @DisplayName("another rep should neither see nor decide on the draft (OWASP A01)")
    void otherRepShouldNotSeeDraft() throws DomainException {
        OrderDraftOutputDTO draft = proposeLine(3);
        DraftDecisionInputDTO asOther = new DraftDecisionInputDTO(otherMsrId, draft.id());

        assertThrows(DraftNotFoundException.class, () -> get.execute(asOther));
        assertThrows(DraftNotFoundException.class, () -> confirm.execute(asOther));
        assertThrows(DraftNotFoundException.class, () -> discard.execute(asOther));
        assertThrows(DraftNotFoundException.class, () -> get.execute(new DraftDecisionInputDTO(msrId, "not-a-uuid")));
        verify(orders, never()).placeOrder(anyList());
    }

    @Test
    @DisplayName("a discarded draft should not be confirmable")
    void discardShouldClose() throws DomainException {
        OrderDraftOutputDTO draft = proposeLine(3);

        assertEquals("DISCARDED", discard.execute(new DraftDecisionInputDTO(msrId, draft.id())).status());
        assertThrows(BusinessValidationException.class,
                () -> confirm.execute(new DraftDecisionInputDTO(msrId, draft.id())));
    }

    @Test
    @DisplayName("proposing should reject no lines or more than 20")
    void proposeShouldEnforceLineCount() {
        assertThrows(BusinessValidationException.class, () -> propose.execute(
                new ProposeOrderDraftInputDTO(msrId, conversationId, List.of(), null, null)));
        List<DraftLineInputDTO> tooMany = java.util.stream.IntStream.range(0, 21)
                .mapToObj(i -> new DraftLineInputDTO(UUID.randomUUID().toString(), 1)).toList();
        assertThrows(BusinessValidationException.class, () -> propose.execute(
                new ProposeOrderDraftInputDTO(msrId, conversationId, tooMany, null, null)));
    }
}
