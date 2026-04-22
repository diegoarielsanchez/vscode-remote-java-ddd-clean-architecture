package com.cleanddd.domain.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.das.cleanddd.domain.settlement.entities.ISettlementRepository;
import com.das.cleanddd.domain.settlement.entities.Settlement;
import com.das.cleanddd.domain.settlement.entities.SettlementId;
import com.das.cleanddd.domain.settlement.usecases.dtos.SettlementIDDto;
import com.das.cleanddd.domain.settlement.usecases.dtos.SettlementMapper;
import com.das.cleanddd.domain.settlement.usecases.dtos.SettlementOutputDTO;
import com.das.cleanddd.domain.settlement.usecases.services.GetSettlementByIdUseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

class GetSettlementByIdUseCaseTest {

    private ISettlementRepository repository;
    private SettlementMapper mapper;
    private GetSettlementByIdUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(ISettlementRepository.class);
        mapper = mock(SettlementMapper.class);
        useCase = new GetSettlementByIdUseCase(repository, mapper);
    }

    @Test
    void shouldReturnSettlementWhenFound() throws DomainException {
        String id = UUID.randomUUID().toString();
        SettlementIDDto input = new SettlementIDDto(id);

        Settlement settlement = mock(Settlement.class);
        SettlementOutputDTO expected = new SettlementOutputDTO(
            id, "Q1 Settlement", LocalDate.now(), "OPEN", BigDecimal.ZERO, List.of()
        );

        when(repository.findById(any(SettlementId.class))).thenReturn(Optional.of(settlement));
        when(mapper.outputFromEntity(settlement)).thenReturn(expected);

        SettlementOutputDTO result = useCase.execute(input);
        assertEquals(expected, result);
    }

    @Test
    void shouldThrowWhenInputIsNull() {
        DomainException ex = assertThrows(DomainException.class, () -> useCase.execute(null));
        assertEquals("Settlement id is required.", ex.getMessage());
    }

    @Test
    void shouldThrowWhenIdIsNull() {
        DomainException ex = assertThrows(DomainException.class,
            () -> useCase.execute(new SettlementIDDto(null)));
        assertEquals("Settlement id is required.", ex.getMessage());
    }

    @Test
    void shouldThrowWhenIdIsBlank() {
        DomainException ex = assertThrows(DomainException.class,
            () -> useCase.execute(new SettlementIDDto("  ")));
        assertEquals("Settlement id is required.", ex.getMessage());
    }

    @Test
    void shouldThrowWhenSettlementNotFound() {
        SettlementIDDto input = new SettlementIDDto(UUID.randomUUID().toString());
        when(repository.findById(any(SettlementId.class))).thenReturn(Optional.empty());

        DomainException ex = assertThrows(DomainException.class, () -> useCase.execute(input));
        assertEquals("Settlement not found.", ex.getMessage());
    }
}
