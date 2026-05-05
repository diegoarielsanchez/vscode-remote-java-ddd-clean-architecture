package com.das.cleanddd.domain.settlement.entities;

/**
 * Domain port (outgoing) that expresses the settlement context's dependency on the
 * MedicalSalesRep bounded-context.  The settlement domain only needs to know whether
 * a given rep exists AND is active — everything else about the rep is irrelevant here.
 *
 * The concrete implementation lives in settlement-infra and calls the msr-service
 * over HTTP (or queries a local read-model), keeping the domain free of I/O concerns.
 */
public interface IMedicalSalesRepPort {

    /**
     * Returns {@code true} when a MedicalSalesRep with the given id exists and has
     * active status set to {@code true}; {@code false} otherwise.
     */
    boolean existsAndIsActive(MedicalSalesRepId medicalSalesRepId);
}
