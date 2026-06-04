package com.das.cleanddd.domain.settlement.usecases.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import com.das.cleanddd.domain.settlement.entities.ISettlementRepository;
import com.das.cleanddd.domain.settlement.usecases.dtos.ListSettlementsInputDTO;
import com.das.cleanddd.domain.settlement.usecases.dtos.SettlementMapper;
import com.das.cleanddd.domain.settlement.usecases.dtos.SettlementOutputDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

public final class ListSettlementsUseCase implements UseCase<ListSettlementsInputDTO, List<SettlementOutputDTO>> {

    @Autowired
    private final ISettlementRepository repository;
    @Autowired
    private final SettlementMapper mapper;

    public ListSettlementsUseCase(ISettlementRepository repository, SettlementMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public List<SettlementOutputDTO> execute(ListSettlementsInputDTO input) throws DomainException {
        int pageSize = Math.min(input.pageSize(), 100);
        return mapper.outputFromEntityList(repository.searchAll(input.page(), pageSize));
    }
}
