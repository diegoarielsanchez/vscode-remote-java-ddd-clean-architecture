package com.das.infra.service.visit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.das.cleanddd.domain.shared.AddressValueObject;
import com.das.cleanddd.domain.shared.Identifier;
import com.das.cleanddd.domain.shared.TextValueObject;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;
import com.das.cleanddd.domain.visit.entities.HealthCareProfId;
import com.das.cleanddd.domain.visit.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.visit.entities.VisitId;
import com.das.cleanddd.domain.visit.entities.VisitPlan;
import com.das.cleanddd.domain.visit.entities.VisitDateTime;

@ExtendWith(MockitoExtension.class)
class SQLVisitPlanRepositoryTest {

    @Mock
    private VisitPlanJpaRepository visitPlanJpaRepository;

    private SQLVisitPlanRepository repository() {
        return new SQLVisitPlanRepository(visitPlanJpaRepository);
    }

    @Test
    void deactivateFutureByMedicalSalesRepId_deactivatesOnlyFuturePlansForThatRep() {
        LocalDateTime today = LocalDateTime.now();

        VisitPlanEntity futureMatch = plan("plan-1", today.plusDays(1), "msr-1", "hcp-1", true);
        VisitPlanEntity futureOther = plan("plan-2", today.plusDays(2), "msr-2", "hcp-1", true);
        VisitPlanEntity pastMatch = plan("plan-3", today.minusDays(1), "msr-1", "hcp-1", true);

        when(visitPlanJpaRepository.findAll()).thenReturn(List.of(futureMatch, futureOther, pastMatch));

        repository().deactivateFutureByMedicalSalesRepId("msr-1");

        assertThat(futureMatch.getActive()).isFalse();
        assertThat(futureOther.getActive()).isTrue();
        assertThat(pastMatch.getActive()).isTrue();
        verify(visitPlanJpaRepository, times(1)).save(futureMatch);
    }

    @Test
    void deactivateFutureByHealthCareProfId_deactivatesOnlyFuturePlansForThatProfessional() {
        LocalDateTime today = LocalDateTime.now();

        VisitPlanEntity futureMatch = plan("plan-1", today.plusDays(1), "msr-1", "hcp-1", true);
        VisitPlanEntity futureOther = plan("plan-2", today.plusDays(2), "msr-1", "hcp-2", true);
        VisitPlanEntity pastMatch = plan("plan-3", today.minusDays(1), "msr-1", "hcp-1", true);

        when(visitPlanJpaRepository.findAll()).thenReturn(List.of(futureMatch, futureOther, pastMatch));

        repository().deactivateFutureByHealthCareProfId("hcp-1");

        assertThat(futureMatch.getActive()).isFalse();
        assertThat(futureOther.getActive()).isTrue();
        assertThat(pastMatch.getActive()).isTrue();
        verify(visitPlanJpaRepository, times(1)).save(futureMatch);
    }

    private VisitPlanEntity plan(String id, LocalDateTime visitDateTime, String msrId, String hcpId, boolean active) {
        VisitPlanEntity entity = new VisitPlanEntity();
        entity.setId(id);
        entity.setVisitDateTime(visitDateTime);
        entity.setMedicalSalesRepId(msrId);
        entity.setHealthCareProfId(hcpId);
        entity.setActive(active);
        return entity;
    }

    // ---------------------------------------------------------------------------
    // address
    // ---------------------------------------------------------------------------

    @Test
    void save_persistsAddress_whenPresent() throws BusinessValidationException {
        AddressValueObject address = new AddressValueObject("1 Clinic Rd", "Springfield", "IL", "62701", "USA");
        VisitPlan visitPlan = new VisitPlan(
                new VisitId(UUID.randomUUID().toString()),
                new VisitDateTime(LocalDate.now().plusDays(1).atTime(10, 0)),
                new HealthCareProfId(UUID.randomUUID().toString()),
                new TextValueObject("planned check") {},
                new Identifier(UUID.randomUUID().toString()) {},
                List.of(),
                new MedicalSalesRepId(UUID.randomUUID().toString()),
                true,
                address);

        repository().save(visitPlan);

        ArgumentCaptor<VisitPlanEntity> captor = ArgumentCaptor.forClass(VisitPlanEntity.class);
        verify(visitPlanJpaRepository).save(captor.capture());
        VisitPlanEntity stored = captor.getValue();
        assertThat(stored.getAddressStreet()).isEqualTo("1 Clinic Rd");
        assertThat(stored.getAddressCity()).isEqualTo("Springfield");
        assertThat(stored.getAddressState()).isEqualTo("IL");
        assertThat(stored.getAddressPostalCode()).isEqualTo("62701");
        assertThat(stored.getAddressCountry()).isEqualTo("USA");
    }

    @Test
    void save_leavesAddressColumnsNull_whenAddressAbsent() throws BusinessValidationException {
        VisitPlan visitPlan = new VisitPlan(
                new VisitId(UUID.randomUUID().toString()),
                new VisitDateTime(LocalDate.now().plusDays(1).atTime(10, 0)),
                new HealthCareProfId(UUID.randomUUID().toString()),
                new TextValueObject("planned check") {},
                new Identifier(UUID.randomUUID().toString()) {},
                List.of(),
                new MedicalSalesRepId(UUID.randomUUID().toString()));

        repository().save(visitPlan);

        ArgumentCaptor<VisitPlanEntity> captor = ArgumentCaptor.forClass(VisitPlanEntity.class);
        verify(visitPlanJpaRepository).save(captor.capture());
        assertThat(captor.getValue().getAddressStreet()).isNull();
    }

    @Test
    void search_roundTripsAddress() {
        String id = UUID.randomUUID().toString();
        String msrId = UUID.randomUUID().toString();
        String hcpId = UUID.randomUUID().toString();
        VisitPlanEntity entity = plan(id, LocalDateTime.now().plusDays(1), msrId, hcpId, true);
        entity.setAddressStreet("1 Clinic Rd");
        entity.setAddressCity("Springfield");
        entity.setAddressState("IL");
        entity.setAddressPostalCode("62701");
        entity.setAddressCountry("USA");
        when(visitPlanJpaRepository.findById(id)).thenReturn(Optional.of(entity));

        Optional<VisitPlan> found = repository().search(new Identifier(id) {});

        assertThat(found).isPresent();
        assertThat(found.get().address()).isEqualTo(
                new AddressValueObject("1 Clinic Rd", "Springfield", "IL", "62701", "USA"));
    }

    @Test
    void search_returnsNullAddress_whenNoneWasEverSaved() {
        String id = UUID.randomUUID().toString();
        String msrId = UUID.randomUUID().toString();
        String hcpId = UUID.randomUUID().toString();
        VisitPlanEntity entity = plan(id, LocalDateTime.now().plusDays(1), msrId, hcpId, true);
        when(visitPlanJpaRepository.findById(id)).thenReturn(Optional.of(entity));

        Optional<VisitPlan> found = repository().search(new Identifier(id) {});

        assertThat(found).isPresent();
        assertThat(found.get().address()).isNull();
    }
}