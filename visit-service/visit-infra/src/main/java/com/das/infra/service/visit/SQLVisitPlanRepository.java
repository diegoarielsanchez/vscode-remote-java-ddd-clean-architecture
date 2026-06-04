package com.das.infra.service.visit;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.shared.Identifier;
import com.das.cleanddd.domain.shared.TextValueObject;
import com.das.cleanddd.domain.shared.criteria.Criteria;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;
import com.das.cleanddd.domain.visit.IVisitPlanRepository;
import com.das.cleanddd.domain.visit.entities.HealthCareProfId;
import com.das.cleanddd.domain.visit.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.visit.entities.VisitId;
import com.das.cleanddd.domain.visit.entities.VisitPlan;

@Service
public final class SQLVisitPlanRepository implements IVisitPlanRepository {

    private final VisitPlanJpaRepository visitPlanJpaRepository;

    public SQLVisitPlanRepository(VisitPlanJpaRepository visitPlanJpaRepository) {
        this.visitPlanJpaRepository = visitPlanJpaRepository;
    }

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

    @Override
    public void deactivateFutureByMedicalSalesRepId(String medicalSalesRepId) {
        if (medicalSalesRepId == null || medicalSalesRepId.isBlank()) return;
        LocalDateTime now = LocalDateTime.now();
        visitPlanJpaRepository.findAll().stream()
                .filter(e -> medicalSalesRepId.equals(e.getMedicalSalesRepId())
                        && e.getVisitDateTime() != null
                        && e.getVisitDateTime().isAfter(now)
                        && Boolean.TRUE.equals(e.getActive()))
                .forEach(e -> {
                    e.setActive(false);
                    visitPlanJpaRepository.save(e);
                });
    }

    @Override
    public void deactivateFutureByHealthCareProfId(String healthCareProfId) {
        if (healthCareProfId == null || healthCareProfId.isBlank()) return;
        LocalDateTime now = LocalDateTime.now();
        visitPlanJpaRepository.findAll().stream()
                .filter(e -> healthCareProfId.equals(e.getHealthCareProfId())
                        && e.getVisitDateTime() != null
                        && e.getVisitDateTime().isAfter(now)
                        && Boolean.TRUE.equals(e.getActive()))
                .forEach(e -> {
                    e.setActive(false);
                    visitPlanJpaRepository.save(e);
                });
    }

    @Override
    public boolean existsDuplicateOnDay(LocalDate date, String healthCareProfId, String medicalSalesRepId, String excludeVisitPlanId) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();
        return visitPlanJpaRepository.existsDuplicateOnDay(startOfDay, endOfDay, healthCareProfId, medicalSalesRepId, excludeVisitPlanId);
    }

    private VisitPlan toDomain(VisitPlanEntity entity) {
        String hcpId = entity.getHealthCareProfId();
        if (hcpId == null) {
            throw new IllegalStateException("HealthCareProf ID is null for visit plan: " + entity.getId());
        }
        String msrId = entity.getMedicalSalesRepId();
        if (msrId == null) {
            throw new IllegalStateException("MedicalSalesRep ID is null for visit plan: " + entity.getId());
        }

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
                    new HealthCareProfId(hcpId),
                    visitComments,
                    visitSiteId,
                    List.of(),
                    new MedicalSalesRepId(msrId),
                    entity.getActive() == null ? true : entity.getActive());
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
        entity.setHealthCareProfId(visitPlan.healthCareProfId() != null ? visitPlan.healthCareProfId().value() : null);
        entity.setMedicalSalesRepId(visitPlan.medicalSalesRepId() != null ? visitPlan.medicalSalesRepId().value() : null);
        entity.setActive(visitPlan.isActive());
        return entity;
    }
}
