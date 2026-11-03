package com.das.infra.service.assistant.claude;

import com.das.cleanddd.domain.shared.exceptions.DomainException;

public class AssistantDisabledException extends DomainException {

    public AssistantDisabledException() {
        super("The assistant is not enabled.");
    }
}
