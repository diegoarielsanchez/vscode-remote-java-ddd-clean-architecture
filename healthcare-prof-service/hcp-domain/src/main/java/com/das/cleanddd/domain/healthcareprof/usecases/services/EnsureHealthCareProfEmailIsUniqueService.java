package com.das.cleanddd.domain.healthcareprof.usecases.services;

import java.util.Optional;

import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProf;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfEmail;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfId;
import com.das.cleanddd.domain.healthcareprof.entities.IHealthCareProfRepository;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/**
 * Domain service enforcing the "email must be unique across all Health Care
 * Professionals" invariant.
 *
 * This rule is a cross-aggregate (set-based) invariant: it depends on the
 * state of every other HealthCareProf in the repository, not just the
 * instance being created/updated. The HealthCareProf entity itself must stay
 * free of infrastructure/repository dependencies, so this check cannot live
 * inside the entity - it belongs in a domain service that use cases invoke.
 *
 * Both CreateHealthCareProfUseCase and UpdateHealthCareProfUseCase share this
 * single enforcement point so the rule is applied consistently and cannot be
 * bypassed or diverge between the two code paths.
 */
public final class EnsureHealthCareProfEmailIsUniqueService {

    private final IHealthCareProfRepository repository;

    public EnsureHealthCareProfEmailIsUniqueService(IHealthCareProfRepository repository) {
        this.repository = repository;
    }

    /**
     * @param email       the email being assigned to the Health Care Professional.
     * @param excludingId when updating, the id of the HealthCareProf being updated
     *                    (so it doesn't conflict with its own current email);
     *                    pass {@code null} when creating a new one.
     */
    public void ensureUnique(HealthCareProfEmail email, HealthCareProfId excludingId) throws DomainException {
        Optional<HealthCareProf> existing = repository.findByEmail(email);
        if (existing.isEmpty()) {
            return;
        }
        if (excludingId != null && existing.get().getId().equals(excludingId)) {
            return;
        }
        throw new DomainException("There is already a Health Care Professional with this email.");
    }
}
