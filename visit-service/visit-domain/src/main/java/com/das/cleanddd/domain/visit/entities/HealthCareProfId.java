package com.das.cleanddd.domain.visit.entities;

import com.das.cleanddd.domain.shared.Identifier;

/**
 * Local value object that carries the identity of a HealthCareProf from the
 * hcp bounded-context.  Referencing by ID (not by object) is the canonical
 * DDD approach for cross-service associations in a microservices architecture.
 */
public final class HealthCareProfId extends Identifier {

    public HealthCareProfId(String value) {
        super(value);
    }
}
