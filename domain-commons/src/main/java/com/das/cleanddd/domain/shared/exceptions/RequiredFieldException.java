package com.das.cleanddd.domain.shared.exceptions;

public class RequiredFieldException extends BusinessException {

    public static final String PATTERN = "Field %s is required.";
    
      public RequiredFieldException(String fieldName) {
          super(PATTERN.formatted(fieldName));
      }
  
  
  
  }
