package com.das.cleanddd.domain.healthcareprof.entities;

import com.das.cleanddd.domain.shared.BoolValueObject;

public class HealthCareProfActive extends BoolValueObject {
    public HealthCareProfActive(Boolean value) {
        super(value);
    }

    public HealthCareProfActive() {
        super(false);
    }
}
