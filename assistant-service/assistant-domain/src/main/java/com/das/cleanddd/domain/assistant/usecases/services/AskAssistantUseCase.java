package com.das.cleanddd.domain.assistant.usecases.services;

import java.time.Clock;
import java.util.UUID;

import com.das.cleanddd.domain.assistant.audit.AgentAuditEntry;
import com.das.cleanddd.domain.assistant.audit.AgentAuditType;
import com.das.cleanddd.domain.assistant.ports.AssistantModelVersion;
import com.das.cleanddd.domain.assistant.ports.IAgentAuditTrail;
import com.das.cleanddd.domain.assistant.ports.IAssistantModel;
import com.das.cleanddd.domain.assistant.usecases.dtos.AskAssistantInputDTO;
import com.das.cleanddd.domain.assistant.usecases.dtos.AssistantReplyDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/** One message from the rep to the assistant, and its answer. Both are written to the audit trail. */
public class AskAssistantUseCase implements UseCase<AskAssistantInputDTO, AssistantReplyDTO> {

    public static final int MAX_TEXT_LENGTH = 2000;

    private final IAssistantModel model;
    private final AssistantModelVersion version;
    private final IAgentAuditTrail auditTrail;
    private final Clock clock;

    public AskAssistantUseCase(IAssistantModel model, AssistantModelVersion version, IAgentAuditTrail auditTrail,
                               Clock clock) {
        this.model = model;
        this.version = version;
        this.auditTrail = auditTrail;
        this.clock = clock;
    }

    @Override
    public AssistantReplyDTO execute(AskAssistantInputDTO input) throws DomainException {
        if (input == null || input.medicalSalesRepId() == null || input.medicalSalesRepId().isBlank()) {
            throw new BusinessValidationException("Only a Medical Sales Rep can use the assistant.");
        }
        if (input.text() == null || input.text().isBlank()) {
            throw new BusinessValidationException("The message is empty.");
        }
        if (input.text().length() > MAX_TEXT_LENGTH) {
            throw new BusinessValidationException("The message can be at most " + MAX_TEXT_LENGTH + " characters.");
        }
        String conversationId;
        if (input.conversationId() == null || input.conversationId().isBlank()) {
            conversationId = UUID.randomUUID().toString();
        } else {
            conversationId = input.conversationId();
            ensureStartedBy(conversationId, input.medicalSalesRepId());
        }
        auditTrail.append(AgentAuditEntry.of(conversationId, AgentAuditType.USER_MESSAGE, input.medicalSalesRepId(),
                version.modelId(), version.promptVersion(), input.text(), clock.instant(), null));

        IAssistantModel.AssistantReply reply = model.converse(new IAssistantModel.AssistantTurn(
                input.medicalSalesRepId(), conversationId, input.text(), input.visitId()));

        auditTrail.append(AgentAuditEntry.of(conversationId, AgentAuditType.MODEL_REPLY, AgentAuditEntry.ASSISTANT,
                version.modelId(), version.promptVersion(), reply.text(), clock.instant(), reply.draftId()));
        return new AssistantReplyDTO(conversationId, reply.text(), reply.draftId());
    }

    /** A conversation can only be continued by the rep who started it. */
    private void ensureStartedBy(String conversationId, String medicalSalesRepId) throws ConversationNotFoundException {
        boolean own = auditTrail.forConversation(conversationId).stream()
                .filter(e -> e.type() == AgentAuditType.USER_MESSAGE)
                .findFirst()
                .map(first -> first.actor().equals(medicalSalesRepId))
                .orElse(false);
        if (!own) {
            throw new ConversationNotFoundException();
        }
    }
}
