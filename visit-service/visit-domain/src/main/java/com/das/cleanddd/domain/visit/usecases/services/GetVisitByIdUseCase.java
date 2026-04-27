package com.das.cleanddd.domain.visit.usecases.services;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;

import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import com.das.cleanddd.domain.visit.IVisitRepository;
import com.das.cleanddd.domain.visit.entities.Visit;
import com.das.cleanddd.domain.visit.entities.VisitId;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitIDDto;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitMapper;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitOutputDTO;

public class GetVisitByIdUseCase implements UseCase<VisitIDDto, VisitOutputDTO> {

    @Autowired
    private final IVisitRepository repository;
    @Autowired
    private final VisitMapper mapper;

    public GetVisitByIdUseCase(IVisitRepository repository, VisitMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public VisitOutputDTO execute(VisitIDDto inputDTO) throws DomainException {
        if (inputDTO == null || inputDTO.visitId() == null) {
            throw new DomainException("Visit Id is required.");
        }

        VisitId visitId = new VisitId(inputDTO.visitId());
        Optional<Visit> visit = repository.search(visitId);
        if (visit.isEmpty()) {
            throw new DomainException("Visit not found.");
        }

        return mapper.outputFromEntity(visit.get());
    }
}
