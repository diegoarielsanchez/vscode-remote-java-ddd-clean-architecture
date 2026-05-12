package com.das.cleanddd.domain.medicalsalesrep.usecases.services;

import com.das.cleanddd.domain.medicalsalesrep.entities.*;
import com.das.cleanddd.domain.medicalsalesrep.ports.IMsrEventPublisher;
import com.das.cleanddd.domain.medicalsalesrep.usecases.dtos.MedicalSalesRepIDDto;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ActivateMedicalSalesRepUseCase")
class ActivateMedicalSalesRepUseCaseTest {

    @Mock private IMedicalSalesRepRepository repository;
    @Mock private IMsrEventPublisher publisher;

    private ActivateMedicalSalesRepUseCase useCase;

    private MedicalSalesRepId msrId;
    private MedicalSalesRep inactiveMsr;
    private MedicalSalesRep activeMsr;

    @BeforeEach
    void setUp() {
        useCase = new ActivateMedicalSalesRepUseCase(repository, publisher);

        msrId = MedicalSalesRepId.random();
        MedicalSalesRepName name    = new MedicalSalesRepName("John");
        MedicalSalesRepName surname = new MedicalSalesRepName("Smith");
        MedicalSalesRepEmail email  = new MedicalSalesRepEmail("john@pharma.com");

        inactiveMsr = new MedicalSalesRep(msrId, name, surname, email, new MedicalSalesRepActive(false));
        activeMsr   = new MedicalSalesRep(msrId, name, surname, email, new MedicalSalesRepActive(true));
    }

    // ── happy path ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("should activate an inactive MSR and persist the change")
    void shouldActivateInactiveMsr() throws DomainException {
        when(repository.findById(msrId)).thenReturn(Optional.of(inactiveMsr));

        useCase.execute(new MedicalSalesRepIDDto(msrId.value()));

        ArgumentCaptor<MedicalSalesRep> captor = ArgumentCaptor.forClass(MedicalSalesRep.class);
        verify(repository, times(1)).save(captor.capture());
        assertTrue(captor.getValue().isActive());
    }

    @Test
    @DisplayName("should publish an MsrActivatedEvent after activation")
    void shouldPublishEventOnActivation() throws DomainException {
        when(repository.findById(msrId)).thenReturn(Optional.of(inactiveMsr));

        useCase.execute(new MedicalSalesRepIDDto(msrId.value()));

        verify(publisher, times(1)).publish(any());
    }

    @Test
    @DisplayName("should not save or publish when MSR is already active")
    void shouldSkipWhenAlreadyActive() throws DomainException {
        when(repository.findById(msrId)).thenReturn(Optional.of(activeMsr));

        useCase.execute(new MedicalSalesRepIDDto(msrId.value()));

        verify(repository, never()).save(any());
        verify(publisher,  never()).publish(any());
    }

    // ── error cases ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("should throw DomainException when id is null")
    void shouldThrowWhenIdNull() {
        assertThrows(DomainException.class,
                () -> useCase.execute(new MedicalSalesRepIDDto(null)));
    }

    @Test
    @DisplayName("should throw DomainException when MSR is not found")
    void shouldThrowWhenNotFound() {
        when(repository.findById(any())).thenReturn(Optional.empty());

        DomainException ex = assertThrows(DomainException.class,
                () -> useCase.execute(new MedicalSalesRepIDDto(msrId.value())));
        assertTrue(ex.getMessage().toLowerCase().contains("not found"));
    }

    @Test
    @DisplayName("activated MSR should preserve the original id")
    void shouldPreserveIdOnActivation() throws DomainException {
        when(repository.findById(msrId)).thenReturn(Optional.of(inactiveMsr));

        useCase.execute(new MedicalSalesRepIDDto(msrId.value()));

        ArgumentCaptor<MedicalSalesRep> captor = ArgumentCaptor.forClass(MedicalSalesRep.class);
        verify(repository).save(captor.capture());
        assertEquals(msrId, captor.getValue().getId());
    }
}
