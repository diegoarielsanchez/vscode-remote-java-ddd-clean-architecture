package com.das.cleanddd.domain.assistant.usecases.services;

import java.time.Clock;

import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.assistant.entities.IOrderDraftRepository;
import com.das.cleanddd.domain.assistant.ports.AssistantModelVersion;
import com.das.cleanddd.domain.assistant.ports.IAgentAuditTrail;
import com.das.cleanddd.domain.assistant.ports.IAssistantModel;
import com.das.cleanddd.domain.assistant.ports.IOrderPort;
import com.das.cleanddd.domain.assistant.usecases.dtos.AskAssistantInputDTO;
import com.das.cleanddd.domain.assistant.usecases.dtos.AssistantReplyDTO;
import com.das.cleanddd.domain.assistant.usecases.dtos.DraftDecisionInputDTO;
import com.das.cleanddd.domain.assistant.usecases.dtos.OrderDraftMapper;
import com.das.cleanddd.domain.assistant.usecases.dtos.OrderDraftOutputDTO;
import com.das.cleanddd.domain.shared.UseCase;

/** The use cases the rep reaches through the REST API. */
@Service
public class AssistantUseCaseFactory {

    private final AskAssistantUseCase askAssistantUseCase;
    private final GetDraftUseCase getDraftUseCase;
    private final ConfirmDraftUseCase confirmDraftUseCase;
    private final DiscardDraftUseCase discardDraftUseCase;

    public AssistantUseCaseFactory(IAssistantModel model, IOrderDraftRepository repository, IOrderPort orders,
                                   IAgentAuditTrail auditTrail, AssistantModelVersion version, Clock clock,
                                   DraftProposalFactory proposals) {
        OrderDraftMapper mapper = new OrderDraftMapper();
        this.askAssistantUseCase = new AskAssistantUseCase(model, version, auditTrail, clock);
        this.getDraftUseCase = new GetDraftUseCase(repository, mapper, clock);
        this.confirmDraftUseCase = new ConfirmDraftUseCase(repository, proposals.getDraftChecker(), orders,
                auditTrail, version, mapper, clock);
        this.discardDraftUseCase = new DiscardDraftUseCase(repository, auditTrail, version, mapper, clock);
    }

    public UseCase<AskAssistantInputDTO, AssistantReplyDTO> getAskAssistantUseCase() {
        return askAssistantUseCase;
    }

    public UseCase<DraftDecisionInputDTO, OrderDraftOutputDTO> getGetDraftUseCase() {
        return getDraftUseCase;
    }

    public UseCase<DraftDecisionInputDTO, OrderDraftOutputDTO> getConfirmDraftUseCase() {
        return confirmDraftUseCase;
    }

    public UseCase<DraftDecisionInputDTO, OrderDraftOutputDTO> getDiscardDraftUseCase() {
        return discardDraftUseCase;
    }
}
