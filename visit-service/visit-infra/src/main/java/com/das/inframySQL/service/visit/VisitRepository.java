package com.das.inframySQL.service.visit;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfId;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.shared.Identifier;
import com.das.cleanddd.domain.shared.criteria.Criteria;
import com.das.cleanddd.domain.visit.IVisitRepository;
import com.das.cleanddd.domain.visit.entities.Visit;

@Service
public final class VisitRepository implements IVisitRepository {
    private final Map<String, Visit> visits = new LinkedHashMap<>();

    public VisitRepository() {
    }

    @Override
    public synchronized void save(Visit visit) {
        if (visit == null || visit.visitId() == null) {
            return;
        }
        visits.put(visit.visitId().value(), visit);
    }

    @Override
    public Optional<Visit> search(Identifier id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(visits.get(id.value()));
    }

    @Override
    public List<Visit> matching(Criteria criteria) {
        return new ArrayList<>(visits.values());
    }

    @Override
    public synchronized List<Visit> searchAll() {
        return new ArrayList<>(visits.values());
    }

    @Override
    public synchronized boolean existsByVisitKey(HealthCareProfId healthCareProfId, MedicalSalesRepId medicalSalesRepId, LocalDateTime visitDate) {
        if (healthCareProfId == null || medicalSalesRepId == null || visitDate == null) {
            return false;
        }
        return visits.values().stream().anyMatch(v ->
            v.healthCareProf().getId().value().equals(healthCareProfId.value()) &&
            v.medicalSalesRep().getId().value().equals(medicalSalesRepId.value()) &&
            v.visitDate().equals(visitDate)
        );
    }
}
