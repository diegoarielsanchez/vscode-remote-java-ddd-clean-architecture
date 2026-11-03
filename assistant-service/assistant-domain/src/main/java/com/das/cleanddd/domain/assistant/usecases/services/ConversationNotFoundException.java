package com.das.cleanddd.domain.assistant.usecases.services;

import com.das.cleanddd.domain.shared.exceptions.DomainException;

/** The conversation does not exist, or another rep started it: both answer the same (OWASP A01). */
public class ConversationNotFoundException extends DomainException {

    public ConversationNotFoundException() {
        super("Conversation not found.");
    }
}
