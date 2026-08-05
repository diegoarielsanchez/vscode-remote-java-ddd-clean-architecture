package com.das.cleanddd.domain.visit;

import java.util.List;
import java.util.Optional;

import com.das.cleanddd.domain.shared.Identifier;
import com.das.cleanddd.domain.shared.criteria.Criteria;
import com.das.cleanddd.domain.visit.entities.HealthCareProfId;
import com.das.cleanddd.domain.visit.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.visit.entities.Visit;
import com.das.cleanddd.domain.visit.entities.VisitDateTime;

public interface IVisitRepository {
    void save(Visit visit);

    Optional<Visit> search(Identifier id);

    List<Visit> matching(Criteria criteria);

    List<Visit> searchAll();

    List<Visit> searchAll(int page, int pageSize);

    boolean existsByVisitKey(HealthCareProfId healthCareProfId, MedicalSalesRepId medicalSalesRepId, VisitDateTime visitDate);

}
