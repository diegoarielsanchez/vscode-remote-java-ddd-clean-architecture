package com.das.cleanddd.domain.healthcareprof.usecases.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProf;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfName;
import com.das.cleanddd.domain.healthcareprof.entities.IHealthCareProfRepository;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.HealthCareProfNamesInputDTO;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.HealthCareProfMapper;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.HealthCareProfOutputDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

public class FindHealthCareProfByNameUseCase implements UseCase<HealthCareProfNamesInputDTO, List<HealthCareProfOutputDTO>> {

    @Autowired
    private final IHealthCareProfRepository repository; 
    @Autowired
    private final HealthCareProfMapper mapper;

    public FindHealthCareProfByNameUseCase(IHealthCareProfRepository repository
        , HealthCareProfMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;   
    }

    @Override
    public List<HealthCareProfOutputDTO> execute(HealthCareProfNamesInputDTO inputDTO) throws DomainException {

      HealthCareProfName name = null;
      HealthCareProfName surname = null;
      // Validate Input
      if(inputDTO==null) {
        inputDTO = new HealthCareProfNamesInputDTO("", "", 1, 10);
      }
      if(inputDTO.page()<=0) {
        inputDTO = new HealthCareProfNamesInputDTO(inputDTO.name(), inputDTO.surname(), 1, inputDTO.pageSize());
      }
      if(inputDTO.pageSize()<=0) {
        inputDTO = new HealthCareProfNamesInputDTO(inputDTO.name(), inputDTO.surname(), inputDTO.page(), 10);
      }
      if(inputDTO.name()==null ) {
        inputDTO = new HealthCareProfNamesInputDTO("", inputDTO.surname(), inputDTO.page(), inputDTO.pageSize());
      } else {
         if (!inputDTO.name().isEmpty()) {
          name = new HealthCareProfName(inputDTO.name());
        }
      }
      if(inputDTO.surname()==null) {
        inputDTO = new HealthCareProfNamesInputDTO(inputDTO.name(), "", inputDTO.page(), inputDTO.pageSize());
      } else {
        if (!inputDTO.surname().isEmpty()) {
          surname = new HealthCareProfName(inputDTO.surname());
        }
      }
      List<HealthCareProf> listHealthCareProfs;
      if (name == null && surname == null) {
        listHealthCareProfs = repository.searchAll();
      } else {
        listHealthCareProfs = repository.findByName(name, surname, inputDTO.page(), inputDTO.pageSize());
      }
      if(listHealthCareProfs.isEmpty()) {
        throw new DomainException("Health Care Professional not found.");
      }    
      // Convert response to output and return
      return mapper.outputFromEntityList(listHealthCareProfs);
  }
}
