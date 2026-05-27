package com.das.cleanddd.domain.visit.usecases.services;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.das.cleanddd.domain.visit.IVisitPlanRepository;

@ExtendWith(MockitoExtension.class)
class DeactivateVisitPlanServiceTest {

    @Mock
    private IVisitPlanRepository visitPlanRepository;

    @Test
    void shouldDelegateToMedicalSalesRepDeactivationRule() {
        DeactivateVisitPlanService service = new DeactivateVisitPlanService(visitPlanRepository);

        service.deactivateByMedicalSalesRepId("msr-1");

        verify(visitPlanRepository).deactivateFutureByMedicalSalesRepId("msr-1");
    }

    @Test
    void shouldDelegateToHealthCareProfDeactivationRule() {
        DeactivateVisitPlanService service = new DeactivateVisitPlanService(visitPlanRepository);

        service.deactivateByHealthCareProfId("hcp-1");

        verify(visitPlanRepository).deactivateFutureByHealthCareProfId("hcp-1");
    }
}