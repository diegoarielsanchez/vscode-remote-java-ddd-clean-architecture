package com.das.cleanddd.domain.assistant.usecases.services;

import java.time.Clock;

import com.das.cleanddd.domain.assistant.entities.IOrderDraftRepository;
import com.das.cleanddd.domain.assistant.usecases.dtos.DraftDecisionInputDTO;
import com.das.cleanddd.domain.assistant.usecases.dtos.OrderDraftMapper;
import com.das.cleanddd.domain.assistant.usecases.dtos.OrderDraftOutputDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

public class GetDraftUseCase implements UseCase<DraftDecisionInputDTO, OrderDraftOutputDTO> {

    private final IOrderDraftRepository repository;
    private final OrderDraftMapper mapper;
    private final Clock clock;

    public GetDraftUseCase(IOrderDraftRepository repository, OrderDraftMapper mapper, Clock clock) {
        this.repository = repository;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public OrderDraftOutputDTO execute(DraftDecisionInputDTO input) throws DomainException {
        return mapper.outputFromEntity(DraftAccess.load(repository, input), clock.instant());
    }
}
