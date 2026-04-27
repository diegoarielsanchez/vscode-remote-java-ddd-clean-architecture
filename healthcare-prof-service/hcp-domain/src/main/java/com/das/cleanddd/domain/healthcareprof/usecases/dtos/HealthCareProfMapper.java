package com.das.cleanddd.domain.healthcareprof.usecases.dtos;

import java.util.List;

import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProf;
import com.das.cleanddd.domain.healthcareprof.entities.Specialty;

@Service
public class HealthCareProfMapper {

    public HealthCareProfOutputDTO outputFromEntity(HealthCareProf entity) {
      return new HealthCareProfOutputDTO(
        entity.getId().value(),
        entity.getName().value(),
        entity.getSurname().value(),
        entity.getEmail().value(),
        entity.getActive().value(),
        entity.getSpecialties() == null
            ? null
            : entity.getSpecialties().stream().map(Specialty::name).toList()
      );
    }
    
    public List<HealthCareProfOutputDTO> outputFromEntityList(List<HealthCareProf> entities) {
        return entities.stream()
            .map(this::outputFromEntity)
            .toList();
    }
  }