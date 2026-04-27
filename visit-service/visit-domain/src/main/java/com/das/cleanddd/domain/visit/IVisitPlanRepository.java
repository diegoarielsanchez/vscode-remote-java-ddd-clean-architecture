package com.das.cleanddd.domain.visit;

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
}
