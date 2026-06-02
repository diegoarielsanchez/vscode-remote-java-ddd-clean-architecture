package com.das.cleanddd.domain.visit.usecases.services;

import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.visit.IVisitPlanRepository;

@Service
public class DeactivateVisitPlanService {

    private final IVisitPlanRepository visitPlanRepository;

    public DeactivateVisitPlanService(IVisitPlanRepository visitPlanRepository) {
        this.visitPlanRepository = visitPlanRepository;
    }

    public void deactivateByMedicalSalesRepId(String medicalSalesRepId) {
        visitPlanRepository.deactivateFutureByMedicalSalesRepId(medicalSalesRepId);
    }

    public void deactivateByHealthCareProfId(String healthCareProfId) {
        visitPlanRepository.deactivateFutureByHealthCareProfId(healthCareProfId);
    }
}