package com.das.cleanddd.domain.shared;

public class UtilsFactory {

    private final ValidationUtils validationUtils = new ValidationUtils();
  
    public ValidationUtils getValidationUtils() {
      return validationUtils;
    }
    
  }