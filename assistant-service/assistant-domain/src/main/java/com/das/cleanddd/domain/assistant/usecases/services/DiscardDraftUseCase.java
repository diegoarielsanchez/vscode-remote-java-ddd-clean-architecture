package com.das.cleanddd.domain.assistant.usecases.services;

import java.time.Clock;

import com.das.cleanddd.domain.assistant.audit.AgentAuditEntry;
import com.das.cleanddd.domain.assistant.audit.AgentAuditType;
import com.das.cleanddd.domain.assistant.entities.IOrderDraftRepository;
import com.das.cleanddd.domain.assistant.entities.OrderDraft;
import com.das.cleanddd.domain.assistant.ports.IAgentAuditTrail;
import com.das.cleanddd.domain.assistant.ports.AssistantModelVersion;
import com.das.cleanddd.domain.assistant.usecases.dtos.DraftDecisionInputDTO;
import com.das.cleanddd.domain.assistant.usecases.dtos.OrderDraftMapper;
import com.das.cleanddd.domain.assistant.usecases.dtos.OrderDraftOutputDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

public class DiscardDraftUseCase implements UseCase<DraftDecisionInputDTO, OrderDraftOutputDTO> {

    private final IOrderDraftRepository repository;
    private final IAgentAuditTrail auditTrail;
    private final AssistantModelVersion version;
    private final OrderDraftMapper mapper;
    private final Clock clock;

    public DiscardDraftUseCase(IOrderDraftRepository repository, IAgentAuditTrail auditTrail, AssistantModelVersion version,
                               OrderDraftMapper mapper, Clock clock) {
        this.repository = repository;
        this.auditTrail = auditTrail;
        this.version = version;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public OrderDraftOutputDTO execute(DraftDecisionInputDTO input) throws DomainException {
        OrderDraft draft = DraftAccess.load(repository, input);
        var now = clock.instant();
        draft.discard(now);
        repository.save(draft);
        auditTrail.append(AgentAuditEntry.of(draft.conversationId(), AgentAuditType.HUMAN_DECISION,
                input.medicalSalesRepId(), version.modelId(), version.promptVersion(), "DISCARDED", now,
                draft.id().value()));
        return mapper.outputFromEntity(draft, now);
    }
}
