package com.das.cleanddd.domain.shared;

import com.das.cleanddd.domain.shared.exceptions.DomainException;

public interface UseCase<I,O> {
  
    public O execute(I inputDTO) throws DomainException;
    
  }
