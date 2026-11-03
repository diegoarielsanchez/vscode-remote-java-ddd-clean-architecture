package com.das.cleanddd.domain.assistant.usecases.services;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.assistant.entities.IOrderDraftRepository;
import com.das.cleanddd.domain.assistant.ports.AssistantModelVersion;
import com.das.cleanddd.domain.assistant.ports.IAgentAuditTrail;
import com.das.cleanddd.domain.assistant.ports.ICatalogPort;
import com.das.cleanddd.domain.assistant.usecases.dtos.OrderDraftMapper;
import com.das.cleanddd.domain.assistant.usecases.dtos.ProposeOrderDraftInputDTO;
import com.das.cleanddd.domain.assistant.usecases.dtos.OrderDraftOutputDTO;
import com.das.cleanddd.domain.shared.UseCase;

/**
 * The use cases the assistant's tools call. Kept apart from {@link AssistantUseCaseFactory}, which needs the model
 * adapter, because the model adapter needs these: one factory for both would depend on itself.
 */
@Service
public class DraftProposalFactory {

    private final DraftChecker draftChecker;
    private final ProposeOrderDraftUseCase proposeOrderDraftUseCase;

    public DraftProposalFactory(IOrderDraftRepository repository, ICatalogPort catalog, IAgentAuditTrail auditTrail,
                                AssistantModelVersion version, Clock clock,
                                @Value("${assistant.draft.ttl:PT2H}") Duration draftTtl) {
        this.draftChecker = new DraftChecker(catalog);
        this.proposeOrderDraftUseCase = new ProposeOrderDraftUseCase(repository, draftChecker, auditTrail, version,
                new OrderDraftMapper(), clock, draftTtl);
    }

    public UseCase<ProposeOrderDraftInputDTO, OrderDraftOutputDTO> getProposeOrderDraftUseCase() {
        return proposeOrderDraftUseCase;
    }

    public DraftChecker getDraftChecker() {
        return draftChecker;
    }
}
