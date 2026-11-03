package com.das.cleanddd.domain.assistant.ports;

import com.das.cleanddd.domain.shared.exceptions.DomainException;

/**
 * The language model behind the assistant. Implemented in infra; the domain neither knows nor cares which model
 * it is, beyond recording its {@link AssistantModelVersion} in the audit trail.
 */
public interface IAssistantModel {

    /** Runs one turn of the conversation, tool calls included, and returns the model's answer to the rep. */
    AssistantReply converse(AssistantTurn turn) throws DomainException;

    /**
     * One message from the rep.
     *
     * @param visitId a visit the rep is asking about, or {@code null}
     */
    record AssistantTurn(String medicalSalesRepId, String conversationId, String text, String visitId) {}

    /** @param draftId the draft proposed in this turn, or {@code null} */
    record AssistantReply(String text, String draftId) {}
}
