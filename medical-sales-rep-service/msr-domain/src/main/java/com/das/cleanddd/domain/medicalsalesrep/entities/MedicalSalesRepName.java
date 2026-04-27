package com.das.cleanddd.domain.medicalsalesrep.entities;

import com.das.cleanddd.domain.shared.StringValueObject;

//import javax.validation.constraints.Size;

public class MedicalSalesRepName extends StringValueObject {
    public static final int MAX_LENGTH = 100;
    public static final int MIN_LENGTH = 2;
    public static final String REGEX = "^[A-Za-zÀ-ÖØ-öø-ÿ\\s]+$"; //"^[a-zA-Z\\s]+$";
    //public static final String ERROR_MESSAGE = "Name must be between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters long and contain only letters and spaces.";
    public static final String ERROR_MESSAGE_NULL = "Name cannot be null.";
    public static final String ERROR_MESSAGE_EMPTY = "Name cannot be empty.";
    public static final String ERROR_MESSAGE_INVALID = "Name must be between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters long and contain only letters and spaces.";
    
    //@Size(min = 2, message = "{validation.name.size.too_short}")
    //@Size(max = 200, message = "{validation.name.size.too_long}")
    public MedicalSalesRepName(String value) {
        super(value); // Explicitly calling the constructor of StringValueObject with the value
        if (value == null) {
            throw new IllegalArgumentException(ERROR_MESSAGE_NULL);
        }
        if (value.isEmpty()) {
            throw new IllegalArgumentException(ERROR_MESSAGE_EMPTY);
        }
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(ERROR_MESSAGE_INVALID);
        }
        if (!value.matches(REGEX)) {
            throw new IllegalArgumentException(ERROR_MESSAGE_INVALID);
        }
 /*        if (value.contains("  ")) {
            throw new IllegalArgumentException(ERROR_MESSAGE_INVALID);
        }
        if (value.startsWith(" ") || value.endsWith(" ")) {
            throw new IllegalArgumentException(ERROR_MESSAGE_INVALID);
        } */
    }

    public MedicalSalesRepName() {
        super("");
    }
}
