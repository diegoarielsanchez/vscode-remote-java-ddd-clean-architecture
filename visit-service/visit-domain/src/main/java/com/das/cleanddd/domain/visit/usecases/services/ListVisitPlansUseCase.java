package com.das.cleanddd.domain.visit.usecases.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import com.das.cleanddd.domain.visit.IVisitPlanRepository;
import com.das.cleanddd.domain.visit.usecases.dtos.ListVisitPlansInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitPlanMapper;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitPlanOutputDTO;

public class ListVisitPlansUseCase implements UseCase<ListVisitPlansInputDTO, List<VisitPlanOutputDTO>> {

    @Autowired
    private final IVisitPlanRepository repository;
    @Autowired
    private final VisitPlanMapper mapper;

    public ListVisitPlansUseCase(IVisitPlanRepository repository, VisitPlanMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public List<VisitPlanOutputDTO> execute(ListVisitPlansInputDTO input) throws DomainException {
        int pageSize = Math.min(input.pageSize(), 100);
        List<com.das.cleanddd.domain.visit.entities.VisitPlan> visitPlans = repository.searchAll(input.page(), pageSize);
        if (visitPlans == null || visitPlans.isEmpty()) {
            throw new DomainException("No visit plans found.");
        }
        return mapper.outputFromEntityList(visitPlans);
    }
}
