package com.das.infrapostgresql.service.healthcareprof;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProf;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfActive;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfEmail;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfId;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfName;
import com.das.cleanddd.domain.healthcareprof.entities.IHealthCareProfRepository;
import com.das.cleanddd.domain.healthcareprof.entities.Specialty;
import com.das.cleanddd.domain.shared.criteria.Criteria;

@Primary
@Service
public final class PostgreSQLHealthCareProfRepository implements IHealthCareProfRepository {

    @Autowired
    private HealthCareProfJpaRepository jpaRepository;

    @Override
    public List<HealthCareProf> searchAll() {
        return jpaRepository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void save(HealthCareProf healthCareProf) {
        HealthCareProfEntity entity = toEntity(healthCareProf);
        if (entity != null) {
            jpaRepository.save(entity);
        }
    }

    @Override
    public List<HealthCareProf> matching(Criteria criteria) {
        return List.of();
    }

    @Override
    public List<HealthCareProf> findByName(HealthCareProfName name, HealthCareProfName surname, int page, int pageSize) {
        String nameValue = name != null ? name.value() : null;
        String surnameValue = surname != null ? surname.value() : null;
        return jpaRepository.findByNameOrSurname(nameValue, surnameValue).stream()
                .map(this::toDomain)
                .skip((long) (page - 1) * pageSize)
                .limit(pageSize)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<HealthCareProf> findById(HealthCareProfId identifier) {
        if (identifier == null) {
            return Optional.empty();
        }
        String id = identifier.value();
        if (id == null) {
            return Optional.empty();
        }
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<HealthCareProf> findByEmail(HealthCareProfEmail email) {
        return jpaRepository.findByEmail(email.value()).map(this::toDomain);
    }

    @Override
    public List<HealthCareProf> findBySpecialty(String specialtyCode, int page, int pageSize) {
        return jpaRepository.findBySpecialtyCode(specialtyCode).stream()
                .map(this::toDomain)
                .skip((long) (page - 1) * pageSize)
                .limit(pageSize)
                .collect(Collectors.toList());
    }

    private HealthCareProf toDomain(HealthCareProfEntity entity) {
        List<Specialty> specialties = entity.getSpecialties() == null
                ? new ArrayList<>()
                : entity.getSpecialties().stream()
                        .map(s -> {
                            String[] parts = s.split("\\|", 2);
                            return parts.length == 2
                                    ? new Specialty(parts[0], parts[1])
                                    : new Specialty(s, s);
                        })
                        .collect(Collectors.toList());

        return new HealthCareProf(
                new HealthCareProfId(entity.getId()),
                new HealthCareProfName(entity.getName()),
                new HealthCareProfName(entity.getSurname()),
                new HealthCareProfEmail(entity.getEmail()),
                new HealthCareProfActive(entity.getActive()),
                specialties
        );
    }

    private HealthCareProfEntity toEntity(HealthCareProf domain) {
        HealthCareProfEntity entity = new HealthCareProfEntity();
        entity.setId(domain.id().value());
        entity.setName(domain.getName().value());
        entity.setSurname(domain.getSurname().value());
        entity.setEmail(domain.getEmail().value());
        entity.setActive(domain.getActive().value());
        List<String> specialties = domain.getSpecialties() == null
                ? new ArrayList<>()
                : domain.getSpecialties().stream()
                        .map(s -> s.code() + "|" + s.name())
                        .collect(Collectors.toList());
        entity.setSpecialties(specialties);
        return entity;
    }
}
