package com.das.cleanddd.domain.visit;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.das.cleanddd.domain.shared.Identifier;
import com.das.cleanddd.domain.shared.criteria.Criteria;
import com.das.cleanddd.domain.visit.entities.VisitPlan;

public interface IVisitPlanRepository {
    void save(VisitPlan visitPlan);

    Optional<VisitPlan> search(Identifier id);

    List<VisitPlan> matching(Criteria criteria);

    List<VisitPlan> searchAll();

    List<VisitPlan> searchAll(int page, int pageSize);

    void deactivateFutureByMedicalSalesRepId(String medicalSalesRepId);

    void deactivateFutureByHealthCareProfId(String healthCareProfId);

    /**
     * Returns {@code true} if an active visit plan already exists for the given
     * {@code healthCareProfId} and {@code medicalSalesRepId} on the given
     * {@code date}, excluding the plan identified by {@code excludeVisitPlanId}
     * (pass {@code null} when creating a new plan).
     */
    boolean existsDuplicateOnDay(LocalDate date, String healthCareProfId, String medicalSalesRepId, String excludeVisitPlanId);
}
