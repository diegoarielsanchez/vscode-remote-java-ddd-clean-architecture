package com.das.cleanddd.domain.assistant.usecases.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.das.cleanddd.domain.assistant.audit.AgentAuditEntry;
import com.das.cleanddd.domain.assistant.audit.AgentAuditType;
import com.das.cleanddd.domain.assistant.ports.AssistantModelVersion;
import com.das.cleanddd.domain.assistant.ports.IAssistantModel;
import com.das.cleanddd.domain.assistant.usecases.dtos.AskAssistantInputDTO;
import com.das.cleanddd.domain.assistant.usecases.dtos.AssistantReplyDTO;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

@DisplayName("AskAssistantUseCase")
class AskAssistantUseCaseTest {

    private final IAssistantModel model = mock(IAssistantModel.class);
    private final InMemoryFakes.AuditTrail audit = new InMemoryFakes.AuditTrail();
    private final AskAssistantUseCase useCase = new AskAssistantUseCase(model,
            new AssistantModelVersion("claude-opus-5-5", "system-v1:test"), audit, Clock.systemUTC());
    private final String msrId = UUID.randomUUID().toString();

    @Test
    @DisplayName("should start a conversation and audit the question and the answer, attributed to who said them")
    void shouldAuditBothSides() throws DomainException {
        when(model.converse(any())).thenReturn(new IAssistantModel.AssistantReply("Here is a draft.", "draft-1"));

        AssistantReplyDTO reply = useCase.execute(new AskAssistantInputDTO(msrId, null, "Order 5 of A", null));

        assertNotNull(reply.conversationId());
        assertEquals("draft-1", reply.draftId());
        List<AgentAuditEntry> trail = audit.forConversation(reply.conversationId());
        assertEquals(List.of(AgentAuditType.USER_MESSAGE, AgentAuditType.MODEL_REPLY),
                trail.stream().map(AgentAuditEntry::type).toList());
        assertEquals(msrId, trail.get(0).actor());
        assertEquals(AgentAuditEntry.ASSISTANT, trail.get(1).actor());
        assertEquals(AgentAuditEntry.sha256("Order 5 of A"), trail.get(0).payloadSha256());
    }

    @Test
    @DisplayName("should not let another rep continue someone else's conversation (OWASP A01)")
    void shouldNotContinueOthersConversation() throws DomainException {
        when(model.converse(any())).thenReturn(new IAssistantModel.AssistantReply("Hi", null));
        String conversationId = useCase.execute(new AskAssistantInputDTO(msrId, null, "Hello", null)).conversationId();

        assertThrows(ConversationNotFoundException.class, () -> useCase.execute(
                new AskAssistantInputDTO(UUID.randomUUID().toString(), conversationId, "Show me the draft", null)));
        assertThrows(ConversationNotFoundException.class, () -> useCase.execute(
                new AskAssistantInputDTO(msrId, UUID.randomUUID().toString(), "Unknown conversation", null)));
    }

    @Test
    @DisplayName("should refuse a caller without msr_id, an empty message or one over 2000 characters, before the model")
    void shouldValidateBeforeCallingModel() throws DomainException {
        assertThrows(BusinessValidationException.class,
                () -> useCase.execute(new AskAssistantInputDTO(null, null, "Hello", null)));
        assertThrows(BusinessValidationException.class,
                () -> useCase.execute(new AskAssistantInputDTO(msrId, null, " ", null)));
        assertThrows(BusinessValidationException.class,
                () -> useCase.execute(new AskAssistantInputDTO(msrId, null, "x".repeat(2001), null)));
        verify(model, never()).converse(any());
    }
}
