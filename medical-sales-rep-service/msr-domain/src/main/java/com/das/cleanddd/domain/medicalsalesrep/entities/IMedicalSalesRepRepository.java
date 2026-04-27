package com.das.cleanddd.domain.medicalsalesrep.entities;

import java.util.List;
import java.util.Optional;

import com.das.cleanddd.domain.shared.criteria.Criteria;

public interface IMedicalSalesRepRepository {

    void save(MedicalSalesRep medicalSalesRepresentative);

    Optional<MedicalSalesRep> findById(MedicalSalesRepId id);

    List<MedicalSalesRep> findByName(MedicalSalesRepName name, MedicalSalesRepName surname, int page, int pageSize);
    //List<MedicalSalesRep> findByName(String name, String surname, int page, int pageSize);

    List<MedicalSalesRep> matching(Criteria criteria);

    List<MedicalSalesRep> searchAll();

    Optional<MedicalSalesRep> findByEmail(MedicalSalesRepEmail email);

}