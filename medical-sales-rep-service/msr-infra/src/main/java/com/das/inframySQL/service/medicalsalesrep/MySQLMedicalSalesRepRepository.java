package com.das.inframySQL.service.medicalsalesrep;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRep;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepActive;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepEmail;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepName;
import com.das.cleanddd.domain.medicalsalesrep.entities.IMedicalSalesRepRepository;
import org.springframework.context.annotation.Primary;
import com.das.cleanddd.domain.shared.criteria.Criteria;

@Primary
@Service
public final class MySQLMedicalSalesRepRepository implements IMedicalSalesRepRepository {

    @Autowired
    private MedicalSalesRepJpaRepository jpaRepository;

    @Override
    public List<MedicalSalesRep> searchAll() {
        return jpaRepository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void save(MedicalSalesRep medicalSalesRep) {
        MedicalSalesRepEntity entity = toEntity(medicalSalesRep);
        if (entity != null) {
            jpaRepository.save(entity);
        }
    }

    @Override
    public List<MedicalSalesRep> matching(Criteria criteria) {
        // Implement criteria-based search if needed
        return null;
    }

    @Override
    public List<MedicalSalesRep> findByName(MedicalSalesRepName name, MedicalSalesRepName surname, int page, int pageSize) {
        String nameValue = (name != null && !name.value().isBlank()) ? name.value() : null;
        String surnameValue = (surname != null && !surname.value().isBlank()) ? surname.value() : null;
        List<MedicalSalesRepEntity> entities = jpaRepository.findByNameOrSurname(nameValue, surnameValue);
        return entities.stream()
                .map(this::toDomain)
                .skip((long) (page - 1) * pageSize)
                .limit(pageSize)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<MedicalSalesRep> findById(MedicalSalesRepId identifier) {
        String id = identifier.value();
        if (id == null) {
            return Optional.empty();
        }
        return jpaRepository.findById(id)
                .map(this::toDomain);
    }

    @Override
    public Optional<MedicalSalesRep> findByEmail(MedicalSalesRepEmail email) {
        return jpaRepository.findByEmail(email.value())
                .map(this::toDomain);
    }

    private MedicalSalesRep toDomain(MedicalSalesRepEntity entity) {
        return new MedicalSalesRep(
                new MedicalSalesRepId(entity.getId()),
                new MedicalSalesRepName(entity.getName()),
                new MedicalSalesRepName(entity.getSurname()),
                new MedicalSalesRepEmail(entity.getEmail()),
                new MedicalSalesRepActive(entity.getActive())
        );
    }

    private MedicalSalesRepEntity toEntity(MedicalSalesRep domain) {
        MedicalSalesRepEntity entity = new MedicalSalesRepEntity();
        entity.setId(domain.id().value());
        entity.setName(domain.getName().value());
        entity.setSurname(domain.getSurname().value());
        entity.setEmail(domain.getEmail().value());
        entity.setActive(domain.getActive().value());
        return entity;
    }
}
