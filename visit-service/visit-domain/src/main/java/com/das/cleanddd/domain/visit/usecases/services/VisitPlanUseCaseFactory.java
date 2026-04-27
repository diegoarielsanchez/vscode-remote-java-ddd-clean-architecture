package com.das.cleanddd.domain.visit.usecases.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.healthcareprof.entities.IHealthCareProfRepository;
import com.das.cleanddd.domain.medicalsalesrep.entities.IMedicalSalesRepRepository;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.UseCaseOnlyOutput;
import com.das.cleanddd.domain.visit.IVisitPlanRepository;
import com.das.cleanddd.domain.visit.entities.VisitPlanFactory;
import com.das.cleanddd.domain.visit.usecases.dtos.CreateVisitPlanInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.UpdateVisitPlanInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitPlanIDDto;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitPlanMapper;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitPlanOutputDTO;

@Service
public class VisitPlanUseCaseFactory {

    private final CreateVisitPlanUseCase createVisitPlanUseCase;
    private final UpdateVisitPlanUseCase updateVisitPlanUseCase;
    private final GetVisitPlanByIdUseCase getVisitPlanByIdUseCase;
    private final ListVisitPlansUseCase listVisitPlansUseCase;

    public VisitPlanUseCaseFactory(
        IVisitPlanRepository visitPlanRepository,
        IHealthCareProfRepository healthCareProfRepository,
        IMedicalSalesRepRepository medicalSalesRepRepository
    ) {
        VisitPlanMapper mapper = new VisitPlanMapper();
        VisitPlanFactory visitPlanFactory = new VisitPlanFactory();
        this.createVisitPlanUseCase = new CreateVisitPlanUseCase(
            visitPlanRepository,
            healthCareProfRepository,
            medicalSalesRepRepository,
            visitPlanFactory,
            mapper
        );
        this.updateVisitPlanUseCase = new UpdateVisitPlanUseCase(
            visitPlanRepository,
            healthCareProfRepository,
            medicalSalesRepRepository,
            mapper
        );
        this.getVisitPlanByIdUseCase = new GetVisitPlanByIdUseCase(visitPlanRepository, mapper);
        this.listVisitPlansUseCase = new ListVisitPlansUseCase(visitPlanRepository, mapper);
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

    public UseCaseOnlyOutput<List<VisitPlanOutputDTO>> getListVisitPlansUseCase() {
        return listVisitPlansUseCase;
    }
}
