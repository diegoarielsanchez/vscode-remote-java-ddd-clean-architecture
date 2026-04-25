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
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.das.cleanddd.domain.settlement.entities.ISettlementRepository;
import com.das.cleanddd.domain.settlement.entities.Settlement;
import com.das.cleanddd.domain.settlement.entities.SettlementId;
import com.das.cleanddd.domain.settlement.usecases.dtos.CreateInvoiceInputDTO;
import com.das.cleanddd.domain.settlement.usecases.dtos.SettlementMapper;
import com.das.cleanddd.domain.settlement.usecases.dtos.SettlementOutputDTO;
import com.das.cleanddd.domain.settlement.usecases.dtos.UpdateSettlementInputDTO;
import com.das.cleanddd.domain.settlement.usecases.services.UpdateSettlementUseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

class UpdateSettlementUseCaseTest {

    private ISettlementRepository repository;
    private SettlementMapper mapper;
    private UpdateSettlementUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(ISettlementRepository.class);
        mapper = mock(SettlementMapper.class);
        useCase = new UpdateSettlementUseCase(repository, mapper);
    }

    @Test
    void shouldUpdateSettlement() throws DomainException {
        String id = UUID.randomUUID().toString();
        UpdateSettlementInputDTO input = new UpdateSettlementInputDTO(
            id,
            "Updated Description",
            LocalDate.now(),
            List.of()
        );

        Settlement existing = mock(Settlement.class);
        when(existing.status()).thenReturn(Settlement.SettlementStatus.OPEN);
        when(repository.findById(any(SettlementId.class))).thenReturn(Optional.of(existing));

        SettlementOutputDTO expected = new SettlementOutputDTO(
            id, "Updated Description", LocalDate.now(), "OPEN", BigDecimal.ZERO, List.of()
        );
        when(mapper.outputFromEntity(any(Settlement.class))).thenReturn(expected);

        SettlementOutputDTO result = useCase.execute(input);

        assertNotNull(result);
        assertEquals(expected, result);
        verify(repository).save(any(Settlement.class));
    }

    @Test
    void shouldUpdateSettlementWithInvoices() throws DomainException {
        String id = UUID.randomUUID().toString();
        CreateInvoiceInputDTO invoiceDTO = new CreateInvoiceInputDTO(
            "A001000000001",
            LocalDate.now().minusDays(65),
            LocalDate.now().plusDays(27),
            new BigDecimal("1200.00")
        );
        UpdateSettlementInputDTO input = new UpdateSettlementInputDTO(
            id, "Updated with invoice", LocalDate.now(), List.of(invoiceDTO)
        );

        Settlement existing = mock(Settlement.class);
        when(existing.status()).thenReturn(Settlement.SettlementStatus.OPEN);
        when(repository.findById(any(SettlementId.class))).thenReturn(Optional.of(existing));

        SettlementOutputDTO expected = new SettlementOutputDTO(
            id, "Updated with invoice", LocalDate.now(), "OPEN",
            new BigDecimal("1200.00"), List.of()
        );
        when(mapper.outputFromEntity(any(Settlement.class))).thenReturn(expected);

        SettlementOutputDTO result = useCase.execute(input);

        assertNotNull(result);
        verify(repository).save(any(Settlement.class));
    }

    @Test
    void shouldThrowWhenInputIsNull() {
        DomainException ex = assertThrows(DomainException.class, () -> useCase.execute(null));
        assertEquals("Input DTO cannot be null.", ex.getMessage());
        verify(repository, never()).save(any(Settlement.class));
    }

    @Test
    void shouldThrowWhenIdIsNull() {
        UpdateSettlementInputDTO input = new UpdateSettlementInputDTO(
            null, "description", LocalDate.now(), List.of()
        );

        DomainException ex = assertThrows(DomainException.class, () -> useCase.execute(input));
        assertEquals("Settlement id is required.", ex.getMessage());
        verify(repository, never()).save(any(Settlement.class));
    }

    @Test
    void shouldThrowWhenIdIsBlank() {
        UpdateSettlementInputDTO input = new UpdateSettlementInputDTO(
            "  ", "description", LocalDate.now(), List.of()
        );

        DomainException ex = assertThrows(DomainException.class, () -> useCase.execute(input));
        assertEquals("Settlement id is required.", ex.getMessage());
        verify(repository, never()).save(any(Settlement.class));
    }

    @Test
    void shouldThrowWhenDescriptionIsNull() {
        UpdateSettlementInputDTO input = new UpdateSettlementInputDTO(
            UUID.randomUUID().toString(), null, LocalDate.now(), List.of()
        );

        DomainException ex = assertThrows(DomainException.class, () -> useCase.execute(input));
        assertEquals("Settlement description is required.", ex.getMessage());
        verify(repository, never()).save(any(Settlement.class));
    }

    @Test
    void shouldThrowWhenDescriptionIsBlank() {
        UpdateSettlementInputDTO input = new UpdateSettlementInputDTO(
            UUID.randomUUID().toString(), "  ", LocalDate.now(), List.of()
        );

        DomainException ex = assertThrows(DomainException.class, () -> useCase.execute(input));
        assertEquals("Settlement description is required.", ex.getMessage());
        verify(repository, never()).save(any(Settlement.class));
    }

    @Test
    void shouldThrowWhenSettlementDateIsNull() {
        UpdateSettlementInputDTO input = new UpdateSettlementInputDTO(
            UUID.randomUUID().toString(), "description", null, List.of()
        );

        DomainException ex = assertThrows(DomainException.class, () -> useCase.execute(input));
        assertEquals("Settlement date is required.", ex.getMessage());
        verify(repository, never()).save(any(Settlement.class));
    }

    @Test
    void shouldThrowWhenSettlementNotFound() {
        UpdateSettlementInputDTO input = new UpdateSettlementInputDTO(
            UUID.randomUUID().toString(), "description", LocalDate.now(), List.of()
        );

        when(repository.findById(any(SettlementId.class))).thenReturn(Optional.empty());

        DomainException ex = assertThrows(DomainException.class, () -> useCase.execute(input));
        assertEquals("Settlement not found.", ex.getMessage());
        verify(repository, never()).save(any(Settlement.class));
    }
}
