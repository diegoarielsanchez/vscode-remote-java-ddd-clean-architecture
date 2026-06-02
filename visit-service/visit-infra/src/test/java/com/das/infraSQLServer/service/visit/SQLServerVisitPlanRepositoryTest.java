package com.das.infraSQLServer.service.visit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SQLServerVisitPlanRepositoryTest {

    @Mock
    private VisitPlanJpaRepository visitPlanJpaRepository;

    private SQLServerVisitPlanRepository repository() {
        return new SQLServerVisitPlanRepository(visitPlanJpaRepository);
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
}