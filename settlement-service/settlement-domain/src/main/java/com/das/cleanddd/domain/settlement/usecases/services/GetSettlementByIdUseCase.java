package com.das.cleanddd.domain.settlement.usecases.services;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;

import com.das.cleanddd.domain.settlement.entities.ISettlementRepository;
import com.das.cleanddd.domain.settlement.entities.Settlement;
import com.das.cleanddd.domain.settlement.entities.SettlementId;
import com.das.cleanddd.domain.settlement.usecases.dtos.SettlementIDDto;
import com.das.cleanddd.domain.settlement.usecases.dtos.SettlementMapper;
import com.das.cleanddd.domain.settlement.usecases.dtos.SettlementOutputDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

public final class GetSettlementByIdUseCase implements UseCase<SettlementIDDto, SettlementOutputDTO> {

    @Autowired
    private final ISettlementRepository repository;
    @Autowired
    private final SettlementMapper mapper;

    public GetSettlementByIdUseCase(ISettlementRepository repository, SettlementMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public SettlementOutputDTO execute(SettlementIDDto inputDTO) throws DomainException {
        if (inputDTO == null || inputDTO.settlementId() == null || inputDTO.settlementId().isBlank()) {
            throw new DomainException("Settlement id is required.");
        }

        SettlementId settlementId = new SettlementId(inputDTO.settlementId());
        Optional<Settlement> settlement = repository.findById(settlementId);
        if (settlement.isEmpty()) {
            throw new DomainException("Settlement not found.");
        }

        return mapper.outputFromEntity(settlement.get());
    }
}
