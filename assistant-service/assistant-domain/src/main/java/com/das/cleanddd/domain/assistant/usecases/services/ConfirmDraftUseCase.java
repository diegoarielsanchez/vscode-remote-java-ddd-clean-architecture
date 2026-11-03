package com.das.cleanddd.domain.assistant.usecases.services;

import java.time.Clock;

import com.das.cleanddd.domain.assistant.audit.AgentAuditEntry;
import com.das.cleanddd.domain.assistant.audit.AgentAuditType;
import com.das.cleanddd.domain.assistant.entities.IOrderDraftRepository;
import com.das.cleanddd.domain.assistant.entities.OrderDraft;
import com.das.cleanddd.domain.assistant.ports.IAgentAuditTrail;
import com.das.cleanddd.domain.assistant.ports.AssistantModelVersion;
import com.das.cleanddd.domain.assistant.ports.IOrderPort;
import com.das.cleanddd.domain.assistant.usecases.dtos.DraftDecisionInputDTO;
import com.das.cleanddd.domain.assistant.usecases.dtos.OrderDraftMapper;
import com.das.cleanddd.domain.assistant.usecases.dtos.OrderDraftOutputDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/**
 * The rep confirms a draft: the checks run again, and only if none fails is the order placed with order-service,
 * as the rep. This is the only way an assistant draft becomes an order.
 */
public class ConfirmDraftUseCase implements UseCase<DraftDecisionInputDTO, OrderDraftOutputDTO> {

    private final IOrderDraftRepository repository;
    private final DraftChecker checker;
    private final IOrderPort orders;
    private final IAgentAuditTrail auditTrail;
    private final AssistantModelVersion version;
    private final OrderDraftMapper mapper;
    private final Clock clock;

    public ConfirmDraftUseCase(IOrderDraftRepository repository, DraftChecker checker, IOrderPort orders,
                               IAgentAuditTrail auditTrail, AssistantModelVersion version, OrderDraftMapper mapper,
                               Clock clock) {
        this.repository = repository;
        this.checker = checker;
        this.orders = orders;
        this.auditTrail = auditTrail;
        this.version = version;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public OrderDraftOutputDTO execute(DraftDecisionInputDTO input) throws DomainException {
        OrderDraft draft = DraftAccess.load(repository, input);
        draft.ensureOpen(clock.instant());

        draft.recheck(checker.recheck(draft.lines()).checks());
        if (draft.hasFailedCheck()) {
            repository.save(draft);
            throw new BusinessValidationException(
                    "The draft no longer passes its checks; review it and ask the assistant for a new one.");
        }

        String orderId = orders.placeOrder(draft.lines());
        var now = clock.instant();
        draft.confirm(orderId, now);
        repository.save(draft);
        auditTrail.append(AgentAuditEntry.of(draft.conversationId(), AgentAuditType.HUMAN_DECISION,
                input.medicalSalesRepId(), version.modelId(), version.promptVersion(),
                "CONFIRMED order " + orderId, now, draft.id().value()));
        return mapper.outputFromEntity(draft, now);
    }
}
