package com.das.infrapostgresql.service.healthcareprof;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HealthCareProfJpaRepository extends JpaRepository<HealthCareProfEntity, String> {

    Optional<HealthCareProfEntity> findByEmail(String email);

    @Query("SELECT e FROM HealthCareProfEntity e WHERE (:name IS NOT NULL AND e.name = :name) OR (:surname IS NOT NULL AND e.surname = :surname)")
    List<HealthCareProfEntity> findByNameOrSurname(@Param("name") String name, @Param("surname") String surname);

    @Query("SELECT DISTINCT e FROM HealthCareProfEntity e JOIN e.specialties s WHERE s LIKE CONCAT(:code, '|%') OR s = :code")
    List<HealthCareProfEntity> findBySpecialtyCode(@Param("code") String code);
}
