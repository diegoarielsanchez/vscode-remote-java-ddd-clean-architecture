package com.das.inframySQL.service.visit;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProf;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfId;
import com.das.cleanddd.domain.healthcareprof.entities.IHealthCareProfRepository;
import com.das.cleanddd.domain.medicalsalesrep.entities.IMedicalSalesRepRepository;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRep;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.shared.Identifier;
import com.das.cleanddd.domain.shared.TextValueObject;
import com.das.cleanddd.domain.shared.criteria.Criteria;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;
import com.das.cleanddd.domain.visit.IVisitPlanRepository;
import com.das.cleanddd.domain.visit.entities.VisitId;
import com.das.cleanddd.domain.visit.entities.VisitPlan;

@Service
public final class MySQLVisitPlanRepository implements IVisitPlanRepository {

    @Autowired
    private VisitPlanJpaRepository visitPlanJpaRepository;

    @Autowired
    private IMedicalSalesRepRepository medicalSalesRepRepository;

    @Autowired
    private IHealthCareProfRepository healthCareProfRepository;

    @Override
    public void save(VisitPlan visitPlan) {
        VisitPlanEntity entity = toEntity(visitPlan);
        if (entity != null) {
            visitPlanJpaRepository.save(entity);
        }
    }

    @Override
    public Optional<VisitPlan> search(Identifier id) {
        if (id == null || id.value() == null) {
            return Optional.empty();
        }
        return visitPlanJpaRepository.findById(id.value()).map(this::toDomain);
    }

    @Override
    public List<VisitPlan> matching(Criteria criteria) {
        return visitPlanJpaRepository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<VisitPlan> searchAll() {
        return visitPlanJpaRepository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    private VisitPlan toDomain(VisitPlanEntity entity) {
        String msrId = entity.getMedicalSalesRepId();
        if (msrId == null) {
            throw new IllegalStateException("MedicalSalesRep ID is null for visit plan: " + entity.getId());
        }
        MedicalSalesRep medicalSalesRep = medicalSalesRepRepository
                .findById(new MedicalSalesRepId(msrId))
                .orElseThrow(() -> new IllegalStateException("MedicalSalesRep not found: " + msrId));

        HealthCareProf healthCareProf = healthCareProfRepository
                .findById(new HealthCareProfId(entity.getHealthCareProfId()))
                .orElseThrow(() -> new IllegalStateException("HealthCareProf not found: " + entity.getHealthCareProfId()));

        TextValueObject visitComments = entity.getVisitComments() != null
                ? new TextValueObject(entity.getVisitComments()) {}
                : null;

        Identifier visitSiteId = entity.getVisitSiteId() != null
                ? new Identifier(entity.getVisitSiteId()) {}
                : null;

        try {
            return new VisitPlan(
                    new VisitId(entity.getId()),
                    entity.getVisitDateTime(),
                    healthCareProf,
                    visitComments,
                    visitSiteId,
                    List.of(),
                    medicalSalesRep);
        } catch (BusinessValidationException e) {
            throw new IllegalStateException("Failed to reconstruct VisitPlan from database: " + e.getMessage(), e);
        }
    }

    private VisitPlanEntity toEntity(VisitPlan visitPlan) {
        if (visitPlan == null || visitPlan.visitId() == null) {
            return null;
        }
        VisitPlanEntity entity = new VisitPlanEntity();
        entity.setId(visitPlan.visitId().value());
        entity.setVisitDateTime(visitPlan.visitTimeDate());
        entity.setVisitComments(visitPlan.visitComments() != null ? visitPlan.visitComments().value() : null);
        entity.setVisitSiteId(visitPlan.visitSideId() != null ? visitPlan.visitSideId().value() : null);
        entity.setHealthCareProfId(visitPlan.healthCareProf() != null ? visitPlan.healthCareProf().getId().value() : null);
        entity.setMedicalSalesRepId(visitPlan.medicalSalesRep() != null ? visitPlan.medicalSalesRep().getId().value() : null);
        return entity;
    }
}
