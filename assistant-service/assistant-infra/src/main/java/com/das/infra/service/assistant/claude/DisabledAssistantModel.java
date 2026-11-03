package com.das.infra.service.assistant.claude;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.assistant.ports.IAssistantModel;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/** Stands in for the model while {@code assistant.enabled} is not {@code true}: no model is ever called. */
@Service
@ConditionalOnProperty(name = "assistant.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledAssistantModel implements IAssistantModel {

    @Override
    public AssistantReply converse(AssistantTurn turn) throws DomainException {
        throw new AssistantDisabledException();
    }
}
