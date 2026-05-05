package com.das.cleanddd.domain.visit.ports;

/**
 * Port that verifies a MedicalSalesRep exists and is active in the msr
 * bounded-context.  Implemented in the infrastructure layer to avoid a direct
 * domain-module dependency on msr-domain.
 */
public interface IMedicalSalesRepValidator {

    /**
     * Returns {@code true} if the medical sales representative with the given
     * id exists and is currently active; {@code false} otherwise.
     */
    boolean existsAndActive(String medicalSalesRepId);
}
