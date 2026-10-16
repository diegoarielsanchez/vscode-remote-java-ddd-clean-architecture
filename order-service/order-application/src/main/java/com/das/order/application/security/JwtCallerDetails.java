package com.das.order.application.security;

/**
 * Extra caller facts from the JWT, attached as the Authentication's details.
 *
 * @param medicalSalesRepId the {@code msr_id} claim — the MSR the caller acts as — or {@code null}
 */
public record JwtCallerDetails(String medicalSalesRepId) {}
