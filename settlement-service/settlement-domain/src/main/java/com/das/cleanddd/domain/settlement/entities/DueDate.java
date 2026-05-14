package com.das.cleanddd.domain.settlement.entities;

import java.time.LocalDate;

import com.das.cleanddd.domain.shared.DateValueObject;

/**
 * Value object representing an invoice due date.
 * The field on {@link Invoice} is nullable (no due date means open-ended).
 * When present, the cross-domain rule that due date cannot precede issue date
 * is enforced by the {@link Invoice} constructor.
 */
public final class DueDate extends DateValueObject {

    public DueDate(LocalDate value) {
        super(value);
    }
}
