package com.das.cleanddd.domain.settlement.usecases.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.settlement.entities.IInvoiceFileStorage;
import com.das.cleanddd.domain.settlement.entities.IMedicalSalesRepPort;
import com.das.cleanddd.domain.settlement.entities.ISettlementRepository;
import com.das.cleanddd.domain.settlement.usecases.dtos.CreateSettlementInputDTO;
import com.das.cleanddd.domain.settlement.usecases.dtos.InvoiceOutputDTO;
import com.das.cleanddd.domain.settlement.usecases.dtos.SettlementIDDto;
import com.das.cleanddd.domain.settlement.usecases.dtos.SettlementMapper;
import com.das.cleanddd.domain.settlement.usecases.dtos.SettlementOutputDTO;
import com.das.cleanddd.domain.settlement.usecases.dtos.UpdateSettlementInputDTO;
import com.das.cleanddd.domain.settlement.usecases.dtos.UploadInvoiceFileInputDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.UseCaseOnlyOutput;

@Service
public class SettlementUseCaseFactory {

    private final CreateSettlementUseCase createSettlementUseCase;
    private final UpdateSettlementUseCase updateSettlementUseCase;
    private final GetSettlementByIdUseCase getSettlementByIdUseCase;
    private final ListSettlementsUseCase listSettlementsUseCase;
    private final UploadInvoiceFileUseCase uploadInvoiceFileUseCase;

    public SettlementUseCaseFactory(ISettlementRepository settlementRepository,
                                     IMedicalSalesRepPort medicalSalesRepPort,
                                     IInvoiceFileStorage fileStorage) {
        SettlementMapper mapper = new SettlementMapper();
        this.createSettlementUseCase = new CreateSettlementUseCase(settlementRepository, mapper, medicalSalesRepPort);
        this.updateSettlementUseCase = new UpdateSettlementUseCase(settlementRepository, mapper);
        this.getSettlementByIdUseCase = new GetSettlementByIdUseCase(settlementRepository, mapper);
        this.listSettlementsUseCase = new ListSettlementsUseCase(settlementRepository, mapper);
        this.uploadInvoiceFileUseCase = new UploadInvoiceFileUseCase(settlementRepository, fileStorage, mapper);
    }

    public UseCase<CreateSettlementInputDTO, SettlementOutputDTO> getCreateSettlementUseCase() {
        return createSettlementUseCase;
    }

    public UseCase<UpdateSettlementInputDTO, SettlementOutputDTO> getUpdateSettlementUseCase() {
        return updateSettlementUseCase;
    }

    public UseCase<SettlementIDDto, SettlementOutputDTO> getSettlementByIdUseCase() {
        return getSettlementByIdUseCase;
    }

    public UseCaseOnlyOutput<List<SettlementOutputDTO>> getListSettlementsUseCase() {
        return listSettlementsUseCase;
    }

    public UseCase<UploadInvoiceFileInputDTO, InvoiceOutputDTO> getUploadInvoiceFileUseCase() {
        return uploadInvoiceFileUseCase;
    }
}
