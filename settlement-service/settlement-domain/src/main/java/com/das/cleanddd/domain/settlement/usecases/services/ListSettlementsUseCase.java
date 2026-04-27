package com.das.cleanddd.domain.settlement.usecases.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import com.das.cleanddd.domain.settlement.entities.ISettlementRepository;
import com.das.cleanddd.domain.settlement.usecases.dtos.SettlementMapper;
import com.das.cleanddd.domain.settlement.usecases.dtos.SettlementOutputDTO;
import com.das.cleanddd.domain.shared.UseCaseOnlyOutput;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

public final class ListSettlementsUseCase implements UseCaseOnlyOutput<List<SettlementOutputDTO>> {

    @Autowired
    private final ISettlementRepository repository;
    @Autowired
    private final SettlementMapper mapper;

    public ListSettlementsUseCase(ISettlementRepository repository, SettlementMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public List<SettlementOutputDTO> execute() throws DomainException {
        return mapper.outputFromEntityList(repository.searchAll());
    }
}
