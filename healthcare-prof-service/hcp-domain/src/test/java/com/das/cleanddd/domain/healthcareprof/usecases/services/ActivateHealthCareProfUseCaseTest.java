package com.das.cleanddd.domain.healthcareprof.usecases.services;

import com.das.cleanddd.domain.healthcareprof.entities.*;
import com.das.cleanddd.domain.healthcareprof.ports.IHcpEventPublisher;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.HealthCareProfIDDto;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ActivateHealthCareProfUseCase")
class ActivateHealthCareProfUseCaseTest {

    @Mock private IHealthCareProfRepository repository;
    @Mock private IHcpEventPublisher publisher;

    private ActivateHealthCareProfUseCase useCase;

    private HealthCareProf inactiveHcp;
    private HealthCareProf activeHcp;
    private HealthCareProfId hcpId;

    @BeforeEach
    void setUp() {
        useCase = new ActivateHealthCareProfUseCase(repository, publisher);

        hcpId = HealthCareProfId.random();
        List<Specialty> specialties = List.of(new Specialty("CARD", "Cardiology"));

        inactiveHcp = new HealthCareProf(
                hcpId,
                new HealthCareProfName("John"),
                new HealthCareProfName("Smith"),
                new HealthCareProfEmail("john@hospital.com"),
                new HealthCareProfActive(false),
                specialties);

        activeHcp = new HealthCareProf(
                hcpId,
                new HealthCareProfName("John"),
                new HealthCareProfName("Smith"),
                new HealthCareProfEmail("john@hospital.com"),
                new HealthCareProfActive(true),
                specialties);
    }

    // ── happy path ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("should activate an inactive HealthCareProf and persist the change")
    void shouldActivateInactiveHcp() throws DomainException {
        when(repository.findById(hcpId)).thenReturn(Optional.of(inactiveHcp));

        useCase.execute(new HealthCareProfIDDto(hcpId.value()));

        ArgumentCaptor<HealthCareProf> captor = ArgumentCaptor.forClass(HealthCareProf.class);
        verify(repository, times(1)).save(captor.capture());
        assertTrue(captor.getValue().isActive());
    }

    @Test
    @DisplayName("should publish an HcpActivatedEvent after activation")
    void shouldPublishEventOnActivation() throws DomainException {
        when(repository.findById(hcpId)).thenReturn(Optional.of(inactiveHcp));

        useCase.execute(new HealthCareProfIDDto(hcpId.value()));

        verify(publisher, times(1)).publish(any());
    }

    @Test
    @DisplayName("should not save or publish when HCP is already active")
    void shouldSkipWhenAlreadyActive() throws DomainException {
        when(repository.findById(hcpId)).thenReturn(Optional.of(activeHcp));

        useCase.execute(new HealthCareProfIDDto(hcpId.value()));

        verify(repository, never()).save(any());
        verify(publisher,  never()).publish(any());
    }

    // ── error cases ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("should throw DomainException when id is null")
    void shouldThrowWhenIdNull() {
        assertThrows(DomainException.class,
                () -> useCase.execute(new HealthCareProfIDDto(null)));
    }

    @Test
    @DisplayName("should throw DomainException when HCP is not found")
    void shouldThrowWhenNotFound() {
        when(repository.findById(any())).thenReturn(Optional.empty());

        DomainException ex = assertThrows(DomainException.class,
                () -> useCase.execute(new HealthCareProfIDDto(hcpId.value())));
        assertTrue(ex.getMessage().toLowerCase().contains("not found"));
    }
}
