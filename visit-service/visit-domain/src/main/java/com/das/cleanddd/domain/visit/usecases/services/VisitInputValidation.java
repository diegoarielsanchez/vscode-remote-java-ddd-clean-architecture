package com.das.cleanddd.domain.visit.usecases.services;

import com.das.cleanddd.domain.shared.exceptions.DomainException;

/**
 * Shared presence/format guard for required id-like input fields on Visit
 * create/update DTOs. Both CreateVisitUseCase and UpdateVisitUseCase need to
 * reject null/blank ids with the same field-specific error message before
 * wrapping them into the corresponding Identifier value object; centralizing
 * that check here avoids duplicating (and risking drift of) the same guard
 * clause across both use cases.
 */
final class VisitInputValidation {

    private VisitInputValidation() {
    }

    static void requireNonBlank(String value, String fieldLabel) throws DomainException {
        if (value == null || value.isBlank()) {
            throw new DomainException(fieldLabel + " cannot be null or empty");
        }
    }
}
