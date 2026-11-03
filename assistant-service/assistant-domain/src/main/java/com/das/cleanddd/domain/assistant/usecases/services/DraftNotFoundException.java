package com.das.cleanddd.domain.assistant.usecases.services;

import com.das.cleanddd.domain.shared.exceptions.DomainException;

/**
 * The draft does not exist, or belongs to another rep: both answer the same, so a rep cannot learn which draft
 * ids exist (OWASP A01).
 */
public class DraftNotFoundException extends DomainException {

    public DraftNotFoundException() {
        super("Order draft not found.");
    }
}
