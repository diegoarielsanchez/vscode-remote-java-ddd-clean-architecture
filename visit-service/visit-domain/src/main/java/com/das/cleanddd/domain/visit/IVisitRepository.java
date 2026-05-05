package com.das.cleanddd.domain.visit;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.das.cleanddd.domain.shared.Identifier;
import com.das.cleanddd.domain.shared.criteria.Criteria;
import com.das.cleanddd.domain.visit.entities.HealthCareProfId;
import com.das.cleanddd.domain.visit.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.visit.entities.Visit;

public interface IVisitRepository {
    void save(Visit visit);

    Optional<Visit> search(Identifier id);

    List<Visit> matching(Criteria criteria);

    List<Visit> searchAll();

    boolean existsByVisitKey(HealthCareProfId healthCareProfId, MedicalSalesRepId medicalSalesRepId, LocalDateTime visitDate);

}
