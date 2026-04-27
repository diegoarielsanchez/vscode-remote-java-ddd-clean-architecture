package com.das.cleanddd.domain.visit.usecases.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import com.das.cleanddd.domain.shared.UseCaseOnlyOutput;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import com.das.cleanddd.domain.visit.IVisitRepository;
import com.das.cleanddd.domain.visit.entities.Visit;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitMapper;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitOutputDTO;

public class ListVisitsUseCase implements UseCaseOnlyOutput<List<VisitOutputDTO>> {

    @Autowired
    private final IVisitRepository repository;
    @Autowired
    private final VisitMapper mapper;

    public ListVisitsUseCase(IVisitRepository repository, VisitMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public List<VisitOutputDTO> execute() throws DomainException {
        List<Visit> visits = repository.searchAll();
        if (visits == null || visits.isEmpty()) {
            throw new DomainException("Visit not found.");
        }
        return mapper.outputFromEntityList(visits);
    }
}
