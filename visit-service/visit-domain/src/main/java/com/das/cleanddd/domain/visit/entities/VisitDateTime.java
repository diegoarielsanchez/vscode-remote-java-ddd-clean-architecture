package com.das.cleanddd.domain.visit.entities;

import java.time.LocalDateTime;

import com.das.cleanddd.domain.shared.DateTimeValueObject;

/**
 * Value object representing a visit date and time.
 * Entity-specific business rules (e.g. must be in the future for {@link VisitPlan},
 * must be in the past for {@link Visit}) are enforced by the owning entity constructor.
 */
public final class VisitDateTime extends DateTimeValueObject {

    public VisitDateTime(LocalDateTime value) {
        super(value);
    }
}
