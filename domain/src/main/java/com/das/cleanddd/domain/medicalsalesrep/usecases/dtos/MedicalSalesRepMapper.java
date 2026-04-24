package com.das.cleanddd.domain.medicalsalesrep.usecases.dtos;

import java.util.List;

import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRep;

@Service
public class MedicalSalesRepMapper {

    public MedicalSalesRepOutputDTO outputFromEntity(MedicalSalesRep medicalSalesRep) {
      return new MedicalSalesRepOutputDTO(
        medicalSalesRep.getId().value(),
        medicalSalesRep.getName().value(),
        medicalSalesRep.getSurname().value(),
        medicalSalesRep.getEmail().value(),
        medicalSalesRep.getActive().value()
      );
    }
    
    public List<MedicalSalesRepOutputDTO> outputFromEntityList(List<MedicalSalesRep> medicalSalesReps) {
        return medicalSalesReps.stream()
            .map(this::outputFromEntity)
            .toList();
    }
  }