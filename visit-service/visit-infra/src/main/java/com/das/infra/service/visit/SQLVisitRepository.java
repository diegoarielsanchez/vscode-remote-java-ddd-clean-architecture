package com.das.infra.service.visit;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.shared.AddressValueObject;
import com.das.cleanddd.domain.shared.Identifier;
import com.das.cleanddd.domain.shared.TextValueObject;
import com.das.cleanddd.domain.shared.criteria.Criteria;
import com.das.cleanddd.domain.visit.IVisitRepository;
import com.das.cleanddd.domain.visit.entities.HealthCareProfId;
import com.das.cleanddd.domain.visit.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.visit.entities.Visit;
import com.das.cleanddd.domain.visit.entities.VisitDateTime;
import com.das.cleanddd.domain.visit.entities.VisitId;

@Primary
@Service
public final class SQLVisitRepository implements IVisitRepository {

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
    public List<Visit> searchAll(int page, int pageSize) {
        return visitJpaRepository.findAll(PageRequest.of(page - 1, pageSize)).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsByVisitKey(HealthCareProfId healthCareProfId, MedicalSalesRepId medicalSalesRepId, VisitDateTime visitDate) {
        if (healthCareProfId == null || medicalSalesRepId == null || visitDate == null) {
            return false;
        }
        return visitJpaRepository.existsByVisitKey(
                healthCareProfId.value(),
                medicalSalesRepId.value(),
                visitDate.value());
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

        return Visit.reconstruct(
                new VisitId(entity.getId()),
                new VisitDateTime(entity.getVisitDate()),
                new HealthCareProfId(hcpId),
                visitComments,
                visitSiteId,
                List.of(),
                new MedicalSalesRepId(msrId),
                addressFromEntity(entity.getAddressStreet(), entity.getAddressCity(),
                        entity.getAddressState(), entity.getAddressPostalCode(), entity.getAddressCountry())
        );
    }

    /** Street is the marker for "no address on file" — see the same pattern in SQLMedicalSalesRepRepository. */
    private AddressValueObject addressFromEntity(String street, String city, String state, String postalCode, String country) {
        if (street == null) {
            return null;
        }
        return new AddressValueObject(street, city, state, postalCode, country);
    }

    private VisitEntity toEntity(Visit visit) {
        if (visit == null || visit.visitId() == null) {
            return null;
        }
        VisitEntity entity = new VisitEntity();
        entity.setId(visit.visitId().value());
        entity.setVisitDate(visit.visitDate().value());
        entity.setVisitComments(visit.visitComments() != null ? visit.visitComments().value() : null);
        entity.setVisitSiteId(visit.visitSideId() != null ? visit.visitSideId().value() : null);
        entity.setHealthCareProfId(visit.healthCareProfId() != null ? visit.healthCareProfId().value() : null);
        entity.setMedicalSalesRepId(visit.medicalSalesRepId() != null ? visit.medicalSalesRepId().value() : null);
        AddressValueObject address = visit.address();
        if (address != null) {
            entity.setAddressStreet(address.street());
            entity.setAddressCity(address.city());
            entity.setAddressState(address.state());
            entity.setAddressPostalCode(address.postalCode());
            entity.setAddressCountry(address.country());
        }
        return entity;
    }
}
