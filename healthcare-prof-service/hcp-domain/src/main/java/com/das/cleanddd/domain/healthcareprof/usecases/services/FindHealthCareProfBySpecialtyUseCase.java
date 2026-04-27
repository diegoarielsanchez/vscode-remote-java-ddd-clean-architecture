package com.das.cleanddd.domain.healthcareprof.usecases.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProf;
import com.das.cleanddd.domain.healthcareprof.entities.IHealthCareProfRepository;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.HealthCareProfMapper;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.HealthCareProfOutputDTO;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.HealthCareProfSpecialtyInputDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

public class FindHealthCareProfBySpecialtyUseCase implements UseCase<HealthCareProfSpecialtyInputDTO, List<HealthCareProfOutputDTO>> {

    @Autowired
    private final IHealthCareProfRepository repository;
    @Autowired
    private final HealthCareProfMapper mapper;

    public FindHealthCareProfBySpecialtyUseCase(IHealthCareProfRepository repository,
            HealthCareProfMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public List<HealthCareProfOutputDTO> execute(HealthCareProfSpecialtyInputDTO inputDTO) throws DomainException {
        if (inputDTO == null || inputDTO.specialtyCode() == null || inputDTO.specialtyCode().isBlank()) {
            throw new DomainException("Specialty code cannot be null or empty.");
        }

        int page = inputDTO.page() <= 0 ? 1 : inputDTO.page();
        int pageSize = inputDTO.pageSize() <= 0 ? 10 : inputDTO.pageSize();

        List<HealthCareProf> results = repository.findBySpecialty(
                inputDTO.specialtyCode().trim().toUpperCase(), page, pageSize);

        if (results.isEmpty()) {
            throw new DomainException("No Health Care Professionals found for the given specialty.");
        }

        return mapper.outputFromEntityList(results);
    }
}
