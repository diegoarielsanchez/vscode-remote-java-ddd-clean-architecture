package com.das.cleanddd.domain.visit.usecases.services;

import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.visit.IVisitRepository;
import com.das.cleanddd.domain.visit.entities.VisitFactory;
import com.das.cleanddd.domain.visit.ports.IHealthCareProfValidator;
import com.das.cleanddd.domain.visit.ports.IMedicalSalesRepValidator;
import com.das.cleanddd.domain.visit.ports.IProductPromoAttachmentStorage;
import com.das.cleanddd.domain.visit.usecases.dtos.CreateVisitInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.UpdateVisitInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.ListVisitsInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.UploadAttachmentsInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.UploadAttachmentsOutputDTO;
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
    private final UploadProductPromoAttachmentsUseCase uploadProductPromoAttachmentsUseCase;

    public VisitUseCaseFactory(
        IVisitRepository visitRepository,
        IHealthCareProfValidator healthCareProfValidator,
        IMedicalSalesRepValidator medicalSalesRepValidator,
        IProductPromoAttachmentStorage attachmentStorage
    ) {
        VisitMapper mapper = new VisitMapper();
        VisitFactory visitFactory = new VisitFactory();
        this.createVisitUseCase = new CreateVisitUseCase(
            visitRepository,
            healthCareProfValidator,
            medicalSalesRepValidator,
            visitFactory,
            mapper
        );
        this.updateVisitUseCase = new UpdateVisitUseCase(
            visitRepository,
            healthCareProfValidator,
            medicalSalesRepValidator,
            mapper
        );
        this.getVisitByIdUseCase = new GetVisitByIdUseCase(visitRepository, mapper);
        this.listVisitsUseCase = new ListVisitsUseCase(visitRepository, mapper);
        this.uploadProductPromoAttachmentsUseCase = new UploadProductPromoAttachmentsUseCase(
            visitRepository,
            attachmentStorage
        );
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

    public UseCase<ListVisitsInputDTO, List<VisitOutputDTO>> getListVisitsUseCase() {
        return listVisitsUseCase;
    }

    public UseCase<UploadAttachmentsInputDTO, UploadAttachmentsOutputDTO> getUploadProductPromoAttachmentsUseCase() {
        return uploadProductPromoAttachmentsUseCase;
    }
}
