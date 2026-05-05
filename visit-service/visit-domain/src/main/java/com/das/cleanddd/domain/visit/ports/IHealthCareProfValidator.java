package com.das.cleanddd.domain.visit.ports;

/**
 * Port that verifies a HealthCareProf exists and is active in the hcp
 * bounded-context.  Implemented in the infrastructure layer to avoid a direct
 * domain-module dependency on hcp-domain.
 */
public interface IHealthCareProfValidator {

    /**
     * Returns {@code true} if the healthcare professional with the given id
     * exists and is currently active; {@code false} otherwise.
     */
    boolean existsAndActive(String healthCareProfId);
}
