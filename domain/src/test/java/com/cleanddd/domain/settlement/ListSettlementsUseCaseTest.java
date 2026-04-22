package com.cleanddd.domain.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.das.cleanddd.domain.settlement.entities.ISettlementRepository;
import com.das.cleanddd.domain.settlement.entities.Settlement;
import com.das.cleanddd.domain.settlement.usecases.dtos.SettlementMapper;
import com.das.cleanddd.domain.settlement.usecases.dtos.SettlementOutputDTO;
import com.das.cleanddd.domain.settlement.usecases.services.ListSettlementsUseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

class ListSettlementsUseCaseTest {

    private ISettlementRepository repository;
    private SettlementMapper mapper;
    private ListSettlementsUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(ISettlementRepository.class);
        mapper = mock(SettlementMapper.class);
        useCase = new ListSettlementsUseCase(repository, mapper);
    }

    @Test
    void shouldReturnListOfSettlements() throws DomainException {
        List<Settlement> settlements = List.of(mock(Settlement.class), mock(Settlement.class));
        List<SettlementOutputDTO> expected = List.of(
            new SettlementOutputDTO(UUID.randomUUID().toString(), "S1", LocalDate.now(), "OPEN",  BigDecimal.ZERO, List.of()),
            new SettlementOutputDTO(UUID.randomUUID().toString(), "S2", LocalDate.now(), "CLOSED", BigDecimal.TEN, List.of())
        );

        when(repository.searchAll()).thenReturn(settlements);
        when(mapper.outputFromEntityList(settlements)).thenReturn(expected);

        List<SettlementOutputDTO> result = useCase.execute();

        assertEquals(2, result.size());
        assertEquals(expected, result);
    }

    @Test
    void shouldReturnEmptyListWhenNoSettlementsExist() throws DomainException {
        when(repository.searchAll()).thenReturn(List.of());
        when(mapper.outputFromEntityList(List.of())).thenReturn(List.of());

        List<SettlementOutputDTO> result = useCase.execute();

        assertTrue(result.isEmpty());
    }
}
