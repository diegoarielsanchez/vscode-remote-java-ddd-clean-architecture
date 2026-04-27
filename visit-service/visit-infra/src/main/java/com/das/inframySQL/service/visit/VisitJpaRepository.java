package com.das.inframySQL.service.visit;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VisitJpaRepository extends JpaRepository<VisitEntity, String> {

    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END FROM VisitEntity e " +
           "WHERE e.healthCareProfId = :healthCareProfId " +
           "AND e.medicalSalesRepId = :medicalSalesRepId " +
           "AND e.visitDate = :visitDate")
    boolean existsByVisitKey(
            @Param("healthCareProfId") String healthCareProfId,
            @Param("medicalSalesRepId") String medicalSalesRepId,
            @Param("visitDate") LocalDateTime visitDate);
}
