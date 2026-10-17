package com.das.cleanddd.domain.visit.usecases.services;

import java.time.Clock;

import com.das.cleanddd.domain.visit.IVisitRepository;
import com.das.cleanddd.domain.visit.usecases.dtos.SuggestVisitPlansInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitPlanSuggestionsOutputDTO;
import java.util.List;

import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.visit.IVisitPlanRepository;
import com.das.cleanddd.domain.visit.entities.VisitPlanFactory;
import com.das.cleanddd.domain.visit.ports.IHealthCareProfValidator;
import com.das.cleanddd.domain.visit.ports.IMedicalSalesRepValidator;
import com.das.cleanddd.domain.visit.usecases.dtos.CreateVisitPlanInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.UpdateVisitPlanInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.ListVisitPlansInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitPlanIDDto;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitPlanMapper;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitPlanOutputDTO;

@Service
public class VisitPlanUseCaseFactory {

    private final CreateVisitPlanUseCase createVisitPlanUseCase;
    private final UpdateVisitPlanUseCase updateVisitPlanUseCase;
    private final GetVisitPlanByIdUseCase getVisitPlanByIdUseCase;
    private final ListVisitPlansUseCase listVisitPlansUseCase;
    private final SuggestVisitPlansUseCase suggestVisitPlansUseCase;

    public VisitPlanUseCaseFactory(
        IVisitPlanRepository visitPlanRepository,
        IVisitRepository visitRepository,
        IHealthCareProfValidator healthCareProfValidator,
        IMedicalSalesRepValidator medicalSalesRepValidator
    ) {
        VisitPlanMapper mapper = new VisitPlanMapper();
        VisitPlanFactory visitPlanFactory = new VisitPlanFactory(healthCareProfValidator, medicalSalesRepValidator);
        this.createVisitPlanUseCase = new CreateVisitPlanUseCase(
            visitPlanRepository,
            visitPlanFactory,
            mapper
        );
        this.updateVisitPlanUseCase = new UpdateVisitPlanUseCase(
            visitPlanRepository,
            visitPlanFactory,
            mapper
        );
        this.getVisitPlanByIdUseCase = new GetVisitPlanByIdUseCase(visitPlanRepository, mapper);
        this.listVisitPlansUseCase = new ListVisitPlansUseCase(visitPlanRepository, mapper);
        this.suggestVisitPlansUseCase = new SuggestVisitPlansUseCase(
            visitRepository,
            visitPlanRepository,
            healthCareProfValidator,
            Clock.systemDefaultZone()
        );
    }

    public UseCase<CreateVisitPlanInputDTO, VisitPlanOutputDTO> getCreateVisitPlanUseCase() {
        return createVisitPlanUseCase;
    }

    public UseCase<UpdateVisitPlanInputDTO, VisitPlanOutputDTO> getUpdateVisitPlanUseCase() {
        return updateVisitPlanUseCase;
    }

    public UseCase<VisitPlanIDDto, VisitPlanOutputDTO> getVisitPlanByIdUseCase() {
        return getVisitPlanByIdUseCase;
    }

    public UseCase<ListVisitPlansInputDTO, List<VisitPlanOutputDTO>> getListVisitPlansUseCase() {
        return listVisitPlansUseCase;
    }

    public UseCase<SuggestVisitPlansInputDTO, VisitPlanSuggestionsOutputDTO> getSuggestVisitPlansUseCase() {
        return suggestVisitPlansUseCase;
    }
}
