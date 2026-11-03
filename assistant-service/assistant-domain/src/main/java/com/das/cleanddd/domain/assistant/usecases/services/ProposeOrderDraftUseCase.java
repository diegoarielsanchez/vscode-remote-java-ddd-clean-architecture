package com.das.cleanddd.domain.assistant.usecases.services;

import java.time.Clock;
import java.time.Duration;

import com.das.cleanddd.domain.assistant.audit.AgentAuditEntry;
import com.das.cleanddd.domain.assistant.audit.AgentAuditType;
import com.das.cleanddd.domain.assistant.entities.IOrderDraftRepository;
import com.das.cleanddd.domain.assistant.entities.OrderDraft;
import com.das.cleanddd.domain.assistant.ports.IAgentAuditTrail;
import com.das.cleanddd.domain.assistant.ports.AssistantModelVersion;
import com.das.cleanddd.domain.assistant.usecases.dtos.OrderDraftMapper;
import com.das.cleanddd.domain.assistant.usecases.dtos.OrderDraftOutputDTO;
import com.das.cleanddd.domain.assistant.usecases.dtos.ProposeOrderDraftInputDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/**
 * Behind the {@code propose_order_draft} tool: checks the lines the model proposed and stores the result as a
 * draft for the rep to decide on. Nothing is reserved or ordered.
 */
public class ProposeOrderDraftUseCase implements UseCase<ProposeOrderDraftInputDTO, OrderDraftOutputDTO> {

    private final IOrderDraftRepository repository;
    private final DraftChecker checker;
    private final IAgentAuditTrail auditTrail;
    private final AssistantModelVersion version;
    private final OrderDraftMapper mapper;
    private final Clock clock;
    private final Duration ttl;

    public ProposeOrderDraftUseCase(IOrderDraftRepository repository, DraftChecker checker,
                                    IAgentAuditTrail auditTrail, AssistantModelVersion version, OrderDraftMapper mapper,
                                    Clock clock, Duration ttl) {
        this.repository = repository;
        this.checker = checker;
        this.auditTrail = auditTrail;
        this.version = version;
        this.mapper = mapper;
        this.clock = clock;
        this.ttl = ttl;
    }

    @Override
    public OrderDraftOutputDTO execute(ProposeOrderDraftInputDTO input) throws DomainException {
        if (input == null || input.lines() == null || input.lines().isEmpty()) {
            throw new BusinessValidationException("A draft needs at least one line.");
        }
        if (input.lines().size() > OrderDraft.MAX_LINES) {
            throw new BusinessValidationException("A draft can have at most " + OrderDraft.MAX_LINES + " lines.");
        }
        DraftChecker.Result checked = checker.check(input.lines());
        var now = clock.instant();
        OrderDraft draft = OrderDraft.propose(input.medicalSalesRepId(), input.conversationId(), checked.lines(),
                checked.checks(), input.contextHcpId(), input.rationale(), now, ttl);
        repository.save(draft);
        OrderDraftOutputDTO output = mapper.outputFromEntity(draft, now);
        auditTrail.append(AgentAuditEntry.of(draft.conversationId(), AgentAuditType.PROPOSAL, AgentAuditEntry.ASSISTANT,
                version.modelId(), version.promptVersion(), output.toString(), now, draft.id().value()));
        return output;
    }
}
