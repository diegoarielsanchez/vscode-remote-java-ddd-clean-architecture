package com.das.inframySQL.service.medicalsalesrep;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicalSalesRepJpaRepository extends JpaRepository<MedicalSalesRepEntity, String> {

    Optional<MedicalSalesRepEntity> findByEmail(String email);

    @Query("SELECT e FROM MedicalSalesRepEntity e WHERE (:name IS NOT NULL AND e.name = :name) OR (:surname IS NOT NULL AND e.surname = :surname)")
    List<MedicalSalesRepEntity> findByNameOrSurname(@Param("name") String name, @Param("surname") String surname);
}