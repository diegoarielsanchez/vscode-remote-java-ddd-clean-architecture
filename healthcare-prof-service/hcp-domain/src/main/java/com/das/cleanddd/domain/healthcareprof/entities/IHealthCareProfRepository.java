package com.das.cleanddd.domain.healthcareprof.entities;

import java.util.List;
import java.util.Optional;

import com.das.cleanddd.domain.shared.criteria.Criteria;

public interface IHealthCareProfRepository {

    void save(HealthCareProf entity);

    Optional<HealthCareProf> findById(HealthCareProfId id);

    List<HealthCareProf> findByName(HealthCareProfName name, HealthCareProfName surname, int page, int pageSize);

    List<HealthCareProf> findBySpecialty(String specialtyCode, int page, int pageSize);

    List<HealthCareProf> matching(Criteria criteria);

    List<HealthCareProf> searchAll();

    Optional<HealthCareProf> findByEmail(HealthCareProfEmail email);

}