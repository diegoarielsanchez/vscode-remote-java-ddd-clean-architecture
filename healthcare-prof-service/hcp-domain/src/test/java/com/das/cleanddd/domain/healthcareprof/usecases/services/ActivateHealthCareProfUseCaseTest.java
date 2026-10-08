package com.das.cleanddd.domain.healthcareprof.usecases.services;

import com.das.cleanddd.domain.healthcareprof.entities.*;
import com.das.cleanddd.domain.healthcareprof.ports.IHcpEventPublisher;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.HealthCareProfIDDto;
import com.das.cleanddd.domain.shared.UnitOfWork;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
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
        useCase = new ActivateHealthCareProfUseCase(repository, publisher, UnitOfWork.immediate());

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

    // ── unit of work ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("should load, save and publish inside one unit of work (transactional outbox)")
    void shouldSaveAndPublishInsideTheUnitOfWork() throws DomainException {
        List<String> calls = new ArrayList<>();
        boolean[] inside = {false};
        UnitOfWork recording = new UnitOfWork() {
            @Override
            public <T> T execute(Work<T> work) throws DomainException {
                inside[0] = true;
                try {
                    return work.run();
                } finally {
                    inside[0] = false;
                }
            }
        };
        when(repository.findById(hcpId)).thenAnswer(inv -> { calls.add("find:" + inside[0]); return Optional.of(inactiveHcp); });
        doAnswer(inv -> { calls.add("save:" + inside[0]); return null; }).when(repository).save(any());
        doAnswer(inv -> { calls.add("publish:" + inside[0]); return null; }).when(publisher).publish(any());

        new ActivateHealthCareProfUseCase(repository, publisher, recording).execute(new HealthCareProfIDDto(hcpId.value()));

        assertEquals(List.of("find:true", "save:true", "publish:true"), calls);
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
