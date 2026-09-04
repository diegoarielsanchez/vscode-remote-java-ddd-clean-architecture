package com.das.cleanddd.domain.order.entities;

import com.das.cleanddd.domain.shared.Identifier;

/**
 * Local copy of the MSR identity, deliberately not shared with msr-domain
 * (Anti-Corruption Layer discipline — see visit-domain's own MedicalSalesRepId
 * for the established precedent of this duplication-by-design).
 */
public class MedicalSalesRepId extends Identifier {
    public MedicalSalesRepId(String value) {
        super(value);
    }
}
