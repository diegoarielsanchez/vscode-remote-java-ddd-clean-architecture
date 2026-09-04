package com.das.cleanddd.domain.order.ports;

/**
 * Port that verifies a MedicalSalesRep exists and is active in the msr
 * bounded context. Implemented in infra by calling msr-service's
 * unauthenticated {@code GET /api/v1/medicalsalesrep/{id}/active-status}
 * endpoint (Option A ACL — no local snapshot cache, kept deliberately simple).
 */
public interface IMedicalSalesRepValidator {

    boolean existsAndActive(String medicalSalesRepId);
}
