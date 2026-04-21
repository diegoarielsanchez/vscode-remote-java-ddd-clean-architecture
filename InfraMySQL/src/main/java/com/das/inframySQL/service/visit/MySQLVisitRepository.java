package com.das.inframySQL.service.visit;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProf;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfId;
import com.das.cleanddd.domain.healthcareprof.entities.IHealthCareProfRepository;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRep;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepActive;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepEmail;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepName;
import com.das.cleanddd.domain.shared.Identifier;
import com.das.cleanddd.domain.shared.TextValueObject;
import com.das.cleanddd.domain.shared.criteria.Criteria;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;
import com.das.cleanddd.domain.visit.IVisitRepository;
import com.das.cleanddd.domain.visit.entities.Visit;
import com.das.cleanddd.domain.visit.entities.VisitId;
import com.das.inframySQL.service.medicalsalesrep.MedicalSalesRepEntity;
import com.das.inframySQL.service.medicalsalesrep.MedicalSalesRepJpaRepository;

@Primary
@Service
public final class MySQLVisitRepository implements IVisitRepository {

    @Autowired
    private VisitJpaRepository visitJpaRepository;

    @Autowired
    private MedicalSalesRepJpaRepository medicalSalesRepJpaRepository;

    @Autowired
    private IHealthCareProfRepository healthCareProfRepository;

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
        String medicalSalesRepId = entity.getMedicalSalesRepId();
        if (medicalSalesRepId == null) {
            throw new IllegalStateException("MedicalSalesRep ID is null for visit: " + entity.getId());
        }
        MedicalSalesRepEntity repEntity = medicalSalesRepJpaRepository
                .findById(medicalSalesRepId)
                .orElseThrow(() -> new IllegalStateException(
                        "MedicalSalesRep not found: " + medicalSalesRepId));

        MedicalSalesRep medicalSalesRep = new MedicalSalesRep(
                new MedicalSalesRepId(repEntity.getId()),
                new MedicalSalesRepName(repEntity.getName()),
                new MedicalSalesRepName(repEntity.getSurname()),
                new MedicalSalesRepEmail(repEntity.getEmail()),
                new MedicalSalesRepActive(repEntity.getActive())
        );

        HealthCareProf healthCareProf = healthCareProfRepository
                .findById(new HealthCareProfId(entity.getHealthCareProfId()))
                .orElseThrow(() -> new IllegalStateException(
                        "HealthCareProf not found: " + entity.getHealthCareProfId()));

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
                    healthCareProf,
                    visitComments,
                    visitSiteId,
                    List.of(),
                    medicalSalesRep
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
        entity.setHealthCareProfId(visit.healthCareProf() != null ? visit.healthCareProf().getId().value() : null);
        entity.setMedicalSalesRepId(visit.medicalSalesRep() != null ? visit.medicalSalesRep().getId().value() : null);
        return entity;
    }
}
