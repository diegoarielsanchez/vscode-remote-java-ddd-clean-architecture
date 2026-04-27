package com.das.cleanddd.domain.settlement.usecases.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.settlement.entities.ISettlementRepository;
import com.das.cleanddd.domain.settlement.usecases.dtos.CreateSettlementInputDTO;
import com.das.cleanddd.domain.settlement.usecases.dtos.SettlementIDDto;
import com.das.cleanddd.domain.settlement.usecases.dtos.SettlementMapper;
import com.das.cleanddd.domain.settlement.usecases.dtos.SettlementOutputDTO;
import com.das.cleanddd.domain.settlement.usecases.dtos.UpdateSettlementInputDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.UseCaseOnlyOutput;

@Service
public class SettlementUseCaseFactory {

    private final CreateSettlementUseCase createSettlementUseCase;
    private final UpdateSettlementUseCase updateSettlementUseCase;
    private final GetSettlementByIdUseCase getSettlementByIdUseCase;
    private final ListSettlementsUseCase listSettlementsUseCase;

    public SettlementUseCaseFactory(ISettlementRepository settlementRepository) {
        SettlementMapper mapper = new SettlementMapper();
        this.createSettlementUseCase = new CreateSettlementUseCase(settlementRepository, mapper);
        this.updateSettlementUseCase = new UpdateSettlementUseCase(settlementRepository, mapper);
        this.getSettlementByIdUseCase = new GetSettlementByIdUseCase(settlementRepository, mapper);
        this.listSettlementsUseCase = new ListSettlementsUseCase(settlementRepository, mapper);
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
}
