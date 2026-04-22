package com.cleanddd.domain.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.das.cleanddd.domain.settlement.entities.ISettlementRepository;
import com.das.cleanddd.domain.settlement.entities.Settlement;
import com.das.cleanddd.domain.settlement.usecases.dtos.CreateInvoiceInputDTO;
import com.das.cleanddd.domain.settlement.usecases.dtos.CreateSettlementInputDTO;
import com.das.cleanddd.domain.settlement.usecases.dtos.InvoiceOutputDTO;
import com.das.cleanddd.domain.settlement.usecases.dtos.SettlementMapper;
import com.das.cleanddd.domain.settlement.usecases.dtos.SettlementOutputDTO;
import com.das.cleanddd.domain.settlement.usecases.services.CreateSettlementUseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

class CreateSettlementUseCaseTest {

    private ISettlementRepository repository;
    private SettlementMapper mapper;
    private CreateSettlementUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(ISettlementRepository.class);
        mapper = mock(SettlementMapper.class);
        useCase = new CreateSettlementUseCase(repository, mapper);
    }

    @Test
    void shouldCreateSettlement() throws DomainException {
        CreateSettlementInputDTO input = new CreateSettlementInputDTO(
            "Q1 Settlement",
            LocalDate.now(),
            List.of()
        );

        SettlementOutputDTO expected = new SettlementOutputDTO(
            UUID.randomUUID().toString(),
            "Q1 Settlement",
            LocalDate.now(),
            "OPEN",
            BigDecimal.ZERO,
            List.of()
        );
        when(mapper.outputFromEntity(any(Settlement.class))).thenReturn(expected);

        SettlementOutputDTO result = useCase.execute(input);

        assertNotNull(result);
        assertEquals(expected, result);
        verify(repository).save(any(Settlement.class));
    }

    @Test
    void shouldCreateSettlementWithInvoices() throws DomainException {
        CreateInvoiceInputDTO invoiceDTO = new CreateInvoiceInputDTO(
            "INV-001",
            LocalDate.now().minusDays(5),
            LocalDate.now().plusDays(25),
            new BigDecimal("500.00")
        );
        CreateSettlementInputDTO input = new CreateSettlementInputDTO(
            "Q2 Settlement",
            LocalDate.now(),
            List.of(invoiceDTO)
        );

        SettlementOutputDTO expected = new SettlementOutputDTO(
            UUID.randomUUID().toString(),
            "Q2 Settlement",
            LocalDate.now(),
            "OPEN",
            new BigDecimal("500.00"),
            List.of(new InvoiceOutputDTO(UUID.randomUUID().toString(), "INV-001",
                LocalDate.now().minusDays(5), LocalDate.now().plusDays(25),
                new BigDecimal("500.00"), "DRAFT"))
        );
        when(mapper.outputFromEntity(any(Settlement.class))).thenReturn(expected);

        SettlementOutputDTO result = useCase.execute(input);

        assertNotNull(result);
        assertEquals(expected, result);
        verify(repository).save(any(Settlement.class));
    }

    @Test
    void shouldThrowWhenInputIsNull() {
        DomainException ex = assertThrows(DomainException.class, () -> useCase.execute(null));
        assertEquals("Input DTO cannot be null.", ex.getMessage());
        verify(repository, never()).save(any(Settlement.class));
    }

    @Test
    void shouldThrowWhenDescriptionIsNull() {
        CreateSettlementInputDTO input = new CreateSettlementInputDTO(
            null,
            LocalDate.now(),
            List.of()
        );

        DomainException ex = assertThrows(DomainException.class, () -> useCase.execute(input));
        assertEquals("Settlement description is required.", ex.getMessage());
        verify(repository, never()).save(any(Settlement.class));
    }

    @Test
    void shouldThrowWhenDescriptionIsBlank() {
        CreateSettlementInputDTO input = new CreateSettlementInputDTO(
            "   ",
            LocalDate.now(),
            List.of()
        );

        DomainException ex = assertThrows(DomainException.class, () -> useCase.execute(input));
        assertEquals("Settlement description is required.", ex.getMessage());
        verify(repository, never()).save(any(Settlement.class));
    }

    @Test
    void shouldThrowWhenSettlementDateIsNull() {
        CreateSettlementInputDTO input = new CreateSettlementInputDTO(
            "Q1 Settlement",
            null,
            List.of()
        );

        DomainException ex = assertThrows(DomainException.class, () -> useCase.execute(input));
        assertEquals("Settlement date is required.", ex.getMessage());
        verify(repository, never()).save(any(Settlement.class));
    }
}
