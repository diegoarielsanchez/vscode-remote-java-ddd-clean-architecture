package com.das.cleanddd.domain.healthcareprof.entities;

import java.util.List;

import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.shared.exceptions.BusinessException;
import com.das.cleanddd.domain.shared.exceptions.RequiredFieldException;

@Service
public class HealthCareProfFactory {

    public HealthCareProf createHealthCareProf(HealthCareProfName name, HealthCareProfName surname, HealthCareProfEmail email, List<Specialty> specialties) throws BusinessException {
      //return new DefaultHealthCareProf(HealthCareProfId.random(), name, surname, email);
      return new HealthCareProf (HealthCareProfId.random(), name, surname, email, new HealthCareProfActive(false), specialties);
    }
  
    public HealthCareProf recreateExistingHealthCareProf(HealthCareProfId id, HealthCareProfName name, HealthCareProfName surname, HealthCareProfEmail email, HealthCareProfActive active, List<Specialty> specialties) throws BusinessException {
      if (id == null) {
        throw new RequiredFieldException("id");
      }
      //HealthCareProf existingHealthCareProf = new DefaultHealthCareProf(id, name, surname, email);
      return new HealthCareProf(id, name, surname, email, active, specialties); // keepActiveValueForExistingHealthCareProf(existingHealthCareProf, active);
    }
  }
