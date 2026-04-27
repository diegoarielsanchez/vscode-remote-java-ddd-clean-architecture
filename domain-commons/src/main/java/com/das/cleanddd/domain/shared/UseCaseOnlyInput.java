package com.das.cleanddd.domain.shared;

import com.das.cleanddd.domain.shared.exceptions.DomainException;

public interface UseCaseOnlyInput<I> {
  
    public void execute(I inputDTO) throws DomainException;
    
  }
