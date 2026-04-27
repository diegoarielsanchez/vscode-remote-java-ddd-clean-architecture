package com.das.cleanddd.domain.medicalsalesrep.usecases.services;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;

import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRep;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.medicalsalesrep.entities.IMedicalSalesRepRepository;
import com.das.cleanddd.domain.medicalsalesrep.usecases.dtos.MedicalSalesRepIDDto;
import com.das.cleanddd.domain.medicalsalesrep.usecases.dtos.MedicalSalesRepMapper;
import com.das.cleanddd.domain.medicalsalesrep.usecases.dtos.MedicalSalesRepOutputDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

public class GetMedicalSalesRepByIdUseCase implements UseCase<MedicalSalesRepIDDto, MedicalSalesRepOutputDTO> {

    @Autowired
    private final IMedicalSalesRepRepository repository; 
    @Autowired
    private final MedicalSalesRepMapper mapper;

    public GetMedicalSalesRepByIdUseCase(IMedicalSalesRepRepository repository
    , MedicalSalesRepMapper mapper) {
    this.repository = repository;
    this.mapper = mapper;   
    }
    //@SuppressWarnings("unlikely-arg-type")
    @Override
    public MedicalSalesRepOutputDTO execute(MedicalSalesRepIDDto inputDTO) throws DomainException {
        
        if(inputDTO.medicalSalesRepId()==null) {
            throw new DomainException("Medical Sales Representative Id is required.");
          }
        MedicalSalesRepId medicalSalesRepId = new MedicalSalesRepId(inputDTO.medicalSalesRepId());
        Optional<MedicalSalesRep> medicalSalesRep = repository.findById(medicalSalesRepId);
        if(!medicalSalesRep.isPresent()) {
            throw new DomainException("Medical Sales Representative not found.");
        }
        //return mapper.outputFromEntity(medicalSalesRep);
        return mapper.outputFromEntity(medicalSalesRep.get());
    }
}
