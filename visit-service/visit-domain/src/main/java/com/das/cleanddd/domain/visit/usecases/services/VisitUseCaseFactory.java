package com.das.cleanddd.domain.visit.usecases.services;

import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.healthcareprof.entities.IHealthCareProfRepository;
import com.das.cleanddd.domain.medicalsalesrep.entities.IMedicalSalesRepRepository;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.UseCaseOnlyOutput;
import com.das.cleanddd.domain.visit.IVisitRepository;
import com.das.cleanddd.domain.visit.entities.VisitFactory;
import com.das.cleanddd.domain.visit.usecases.dtos.CreateVisitInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.UpdateVisitInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitIDDto;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitMapper;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitOutputDTO;
import java.util.List;

@Service
public class VisitUseCaseFactory {

    private final CreateVisitUseCase createVisitUseCase;
    private final UpdateVisitUseCase updateVisitUseCase;
    private final GetVisitByIdUseCase getVisitByIdUseCase;
    private final ListVisitsUseCase listVisitsUseCase;

    public VisitUseCaseFactory(
        IVisitRepository visitRepository,
        IHealthCareProfRepository healthCareProfRepository,
        IMedicalSalesRepRepository medicalSalesRepRepository
    ) {
        VisitMapper mapper = new VisitMapper();
        VisitFactory visitFactory = new VisitFactory();
        this.createVisitUseCase = new CreateVisitUseCase(
            visitRepository,
            healthCareProfRepository,
            medicalSalesRepRepository,
            visitFactory,
            mapper
        );
        this.updateVisitUseCase = new UpdateVisitUseCase(
            visitRepository,
            healthCareProfRepository,
            medicalSalesRepRepository,
            mapper
        );
        this.getVisitByIdUseCase = new GetVisitByIdUseCase(visitRepository, mapper);
        this.listVisitsUseCase = new ListVisitsUseCase(visitRepository, mapper);
    }

    public UseCase<CreateVisitInputDTO, VisitOutputDTO> getCreateVisitUseCase() {
        return createVisitUseCase;
    }

    public UseCase<UpdateVisitInputDTO, VisitOutputDTO> getUpdateVisitUseCase() {
        return updateVisitUseCase;
    }

    public UseCase<VisitIDDto, VisitOutputDTO> getVisitByIdUseCase() {
        return getVisitByIdUseCase;
    }

    public UseCaseOnlyOutput<List<VisitOutputDTO>> getListVisitsUseCase() {
        return listVisitsUseCase;
    }
}
