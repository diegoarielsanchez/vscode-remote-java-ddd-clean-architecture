package com.das.cleanddd.domain.medicalsalesrep.usecases.services;

import java.util.Optional;

import com.das.cleanddd.domain.medicalsalesrep.entities.IMedicalSalesRepRepository;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRep;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepEmail;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/**
 * Domain service enforcing the "email must be unique across all Medical Sales
 * Representatives" invariant.
 *
 * This rule is a cross-aggregate (set-based) invariant: it depends on the
 * state of every other MedicalSalesRep in the repository, not just the
 * instance being created/updated. The MedicalSalesRep entity itself must stay
 * free of infrastructure/repository dependencies, so this check cannot live
 * inside the entity - it belongs in a domain service that use cases invoke.
 *
 * Both CreateMedicalSalesRepUseCase and UpdateMedicalSalesRepUseCase share this
 * single enforcement point so the rule is applied consistently and cannot be
 * bypassed or diverge between the two code paths.
 */
public final class EnsureMedicalSalesRepEmailIsUniqueService {

    private final IMedicalSalesRepRepository repository;

    public EnsureMedicalSalesRepEmailIsUniqueService(IMedicalSalesRepRepository repository) {
        this.repository = repository;
    }

    /**
     * @param email       the email being assigned to the Medical Sales Representative.
     * @param excludingId when updating, the id of the MedicalSalesRep being updated
     *                    (so it doesn't conflict with its own current email);
     *                    pass {@code null} when creating a new one.
     */
    public void ensureUnique(MedicalSalesRepEmail email, MedicalSalesRepId excludingId) throws DomainException {
        Optional<MedicalSalesRep> existing = repository.findByEmail(email);
        if (existing.isEmpty()) {
            return;
        }
        if (excludingId != null && existing.get().getId().equals(excludingId)) {
            return;
        }
        throw new DomainException("There is already a Medical Sales Representative with this email.");
    }
}
