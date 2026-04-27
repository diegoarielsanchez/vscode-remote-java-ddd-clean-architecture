package com.das.cleanddd.domain.medicalsalesrep.entities;

import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.shared.exceptions.BusinessException;
import com.das.cleanddd.domain.shared.exceptions.RequiredFieldException;

@Service
public class MedicalSalesRepFactory {

    public MedicalSalesRep createMedicalSalesRep(MedicalSalesRepName name, MedicalSalesRepName surname, MedicalSalesRepEmail email) throws BusinessException {
      //return new DefaultMedicalSalesRep(MedicalSalesRepId.random(), name, surname, email);
      return new MedicalSalesRep (MedicalSalesRepId.random(), name, surname, email, new MedicalSalesRepActive(false));
    }
  
    public MedicalSalesRep recreateExistingMedicalSalesRepresentative(MedicalSalesRepId id, MedicalSalesRepName name, MedicalSalesRepName surname, MedicalSalesRepEmail email, MedicalSalesRepActive active) throws BusinessException {
      if (id == null) {
        throw new RequiredFieldException("id");
      }
      //MedicalSalesRep existingMedicalSalesRepresentative = new DefaultMedicalSalesRep(id, name, surname, email);
      return new MedicalSalesRep(id, name, surname, email, active); // keepActiveValueForExistingMedicalSalesRepresentative(existingMedicalSalesRepresentative, active);
    }
  }
