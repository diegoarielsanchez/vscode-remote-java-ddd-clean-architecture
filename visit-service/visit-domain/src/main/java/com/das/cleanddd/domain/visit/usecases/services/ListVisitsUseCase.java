package com.das.cleanddd.domain.visit.usecases.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import com.das.cleanddd.domain.visit.IVisitRepository;
import com.das.cleanddd.domain.visit.usecases.dtos.ListVisitsInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitMapper;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitOutputDTO;

public class ListVisitsUseCase implements UseCase<ListVisitsInputDTO, List<VisitOutputDTO>> {

    @Autowired
    private final IVisitRepository repository;
    @Autowired
    private final VisitMapper mapper;

    public ListVisitsUseCase(IVisitRepository repository, VisitMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public List<VisitOutputDTO> execute(ListVisitsInputDTO input) throws DomainException {
        int pageSize = Math.min(input.pageSize(), 100);
        List<com.das.cleanddd.domain.visit.entities.Visit> visits = repository.searchAll(input.page(), pageSize);
        if (visits == null || visits.isEmpty()) {
            throw new DomainException("Visit not found.");
        }
        return mapper.outputFromEntityList(visits);
    }
}
