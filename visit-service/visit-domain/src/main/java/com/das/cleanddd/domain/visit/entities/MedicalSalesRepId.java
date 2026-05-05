package com.das.cleanddd.domain.visit.entities;

import com.das.cleanddd.domain.shared.Identifier;

/**
 * Local value object that carries the identity of a MedicalSalesRep from the
 * msr bounded-context.  Referencing by ID (not by object) is the canonical
 * DDD approach for cross-service associations in a microservices architecture.
 */
public final class MedicalSalesRepId extends Identifier {

    public MedicalSalesRepId(String value) {
        super(value);
    }
}
