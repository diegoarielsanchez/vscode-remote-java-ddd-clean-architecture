package com.das.cleanddd.domain.shared;

import com.das.cleanddd.domain.shared.exceptions.DomainException;

public interface UseCaseOnlyOutput<O> {
  
    public O execute() throws DomainException;
    
  }
