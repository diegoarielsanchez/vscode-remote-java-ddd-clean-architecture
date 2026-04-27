package com.das.cleanddd.domain.healthcareprof.usecases.services;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;

import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProf;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfId;
import com.das.cleanddd.domain.healthcareprof.entities.IHealthCareProfRepository;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.HealthCareProfIDDto;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.HealthCareProfMapper;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.HealthCareProfOutputDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

public class GetHealthCareProfByIdUseCase implements UseCase<HealthCareProfIDDto, HealthCareProfOutputDTO> {

    @Autowired
    private final IHealthCareProfRepository repository; 
    @Autowired
    private final HealthCareProfMapper mapper;

    public GetHealthCareProfByIdUseCase(IHealthCareProfRepository repository
    , HealthCareProfMapper mapper) {
    this.repository = repository;
    this.mapper = mapper;   
    }
    //@SuppressWarnings("unlikely-arg-type")
    @Override
    public HealthCareProfOutputDTO execute(HealthCareProfIDDto inputDTO) throws DomainException {
        
        if(inputDTO.id()==null) {
            throw new DomainException("Health Care Professional Id is required.");
          }
        HealthCareProfId id = new HealthCareProfId(inputDTO.id());
        Optional<HealthCareProf> entity = repository.findById(id   );
        if(!entity.isPresent()) {
            throw new DomainException("Health Care Professional not found.");
        }
        //return mapper.outputFromEntity(HealthCareProfRep);
        return mapper.outputFromEntity(entity.get());
    }
}
