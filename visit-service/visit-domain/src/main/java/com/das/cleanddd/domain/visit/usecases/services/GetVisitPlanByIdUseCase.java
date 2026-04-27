package com.das.cleanddd.domain.visit.usecases.services;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;

import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import com.das.cleanddd.domain.visit.IVisitPlanRepository;
import com.das.cleanddd.domain.visit.entities.VisitId;
import com.das.cleanddd.domain.visit.entities.VisitPlan;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitPlanIDDto;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitPlanMapper;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitPlanOutputDTO;

public class GetVisitPlanByIdUseCase implements UseCase<VisitPlanIDDto, VisitPlanOutputDTO> {

    @Autowired
    private final IVisitPlanRepository repository;
    @Autowired
    private final VisitPlanMapper mapper;

    public GetVisitPlanByIdUseCase(IVisitPlanRepository repository, VisitPlanMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public VisitPlanOutputDTO execute(VisitPlanIDDto inputDTO) throws DomainException {
        if (inputDTO == null || inputDTO.visitPlanId() == null) {
            throw new DomainException("Visit plan id is required.");
        }

        VisitId visitId = new VisitId(inputDTO.visitPlanId());
        Optional<VisitPlan> visitPlan = repository.search(visitId);
        if (visitPlan.isEmpty()) {
            throw new DomainException("Visit plan not found.");
        }

        return mapper.outputFromEntity(visitPlan.get());
    }
}
