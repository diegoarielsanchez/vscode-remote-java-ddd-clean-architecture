package com.das.cleanddd.domain.medicalsalesrep.entities;

import com.das.cleanddd.domain.shared.EmailValueObject;

public class MedicalSalesRepEmail extends EmailValueObject {
    public static final String REGEX = EmailValueObject.REGEX;
    public static final String ERROR_MESSAGE_NULL = EmailValueObject.ERROR_MESSAGE_NULL;
    public static final String ERROR_MESSAGE_EMPTY = EmailValueObject.ERROR_MESSAGE_EMPTY;
    public static final String ERROR_MESSAGE_INVALID = EmailValueObject.ERROR_MESSAGE_INVALID;
    public static final String ERROR_MESSAGE_INVALID_FORMAT = EmailValueObject.ERROR_MESSAGE_INVALID_FORMAT;
    public static final String ERROR_MESSAGE_INVALID_LENGTH = EmailValueObject.ERROR_MESSAGE_INVALID_LENGTH;
    public static final int MAX_LENGTH = EmailValueObject.MAX_LENGTH;
    public static final int MIN_LENGTH = EmailValueObject.MIN_LENGTH;
    public static final String ERROR_MESSAGE = EmailValueObject.ERROR_MESSAGE;
    public static final String ERROR_MESSAGE_INVALID_CHARACTERS = EmailValueObject.ERROR_MESSAGE_INVALID_CHARACTERS;

    public MedicalSalesRepEmail(String value) {
        super(value);
    }
}
