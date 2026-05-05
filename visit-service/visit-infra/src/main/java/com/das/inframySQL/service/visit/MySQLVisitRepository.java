package com.das.inframySQL.service.visit;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.shared.Identifier;
import com.das.cleanddd.domain.shared.TextValueObject;
import com.das.cleanddd.domain.shared.criteria.Criteria;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;
import com.das.cleanddd.domain.visit.IVisitRepository;
import com.das.cleanddd.domain.visit.entities.HealthCareProfId;
import com.das.cleanddd.domain.visit.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.visit.entities.Visit;
import com.das.cleanddd.domain.visit.entities.VisitId;

@Primary
@Service
public final class MySQLVisitRepository implements IVisitRepository {

    @Autowired
    private VisitJpaRepository visitJpaRepository;

    @Override
    public void save(Visit visit) {
        VisitEntity entity = toEntity(visit);
        if (entity != null) {
            visitJpaRepository.save(entity);
        }
    }

    @Override
    public Optional<Visit> search(Identifier id) {
        if (id == null) {
            return Optional.empty();
        }
        String idValue = id.value();
        if (idValue == null) {
            return Optional.empty();
        }
        return visitJpaRepository.findById(idValue)
                .map(this::toDomain);
    }

    @Override
    public List<Visit> matching(Criteria criteria) {
        return visitJpaRepository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Visit> searchAll() {
        return visitJpaRepository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsByVisitKey(HealthCareProfId healthCareProfId, MedicalSalesRepId medicalSalesRepId, LocalDateTime visitDate) {
        if (healthCareProfId == null || medicalSalesRepId == null || visitDate == null) {
            return false;
        }
        return visitJpaRepository.existsByVisitKey(
                healthCareProfId.value(),
                medicalSalesRepId.value(),
                visitDate);
    }

    private Visit toDomain(VisitEntity entity) {
        String hcpId = entity.getHealthCareProfId();
        if (hcpId == null) {
            throw new IllegalStateException("HealthCareProf ID is null for visit: " + entity.getId());
        }
        String msrId = entity.getMedicalSalesRepId();
        if (msrId == null) {
            throw new IllegalStateException("MedicalSalesRep ID is null for visit: " + entity.getId());
        }

        TextValueObject visitComments = entity.getVisitComments() != null
                ? new TextValueObject(entity.getVisitComments()) {}
                : null;

        Identifier visitSiteId = entity.getVisitSiteId() != null
                ? new Identifier(entity.getVisitSiteId()) {}
                : null;

        try {
            return new Visit(
                    new VisitId(entity.getId()),
                    entity.getVisitDate(),
                    new HealthCareProfId(hcpId),
                    visitComments,
                    visitSiteId,
                    List.of(),
                    new MedicalSalesRepId(msrId)
            );
        } catch (BusinessValidationException e) {
            throw new IllegalStateException("Failed to reconstruct Visit from database: " + e.getMessage(), e);
        }
    }

    private VisitEntity toEntity(Visit visit) {
        if (visit == null || visit.visitId() == null) {
            return null;
        }
        VisitEntity entity = new VisitEntity();
        entity.setId(visit.visitId().value());
        entity.setVisitDate(visit.visitDate());
        entity.setVisitComments(visit.visitComments() != null ? visit.visitComments().value() : null);
        entity.setVisitSiteId(visit.visitSideId() != null ? visit.visitSideId().value() : null);
        entity.setHealthCareProfId(visit.healthCareProfId() != null ? visit.healthCareProfId().value() : null);
        entity.setMedicalSalesRepId(visit.medicalSalesRepId() != null ? visit.medicalSalesRepId().value() : null);
        return entity;
    }
}
