package com.das.cleanddd.domain.healthcareprof.entities;

import com.das.cleanddd.domain.shared.Identifier;

public class HealthCareProfId extends Identifier{
    public HealthCareProfId(String value) {
        super(value);
    }

    public static HealthCareProfId random() {
        return new HealthCareProfId(java.util.UUID.randomUUID().toString());
    }
}
