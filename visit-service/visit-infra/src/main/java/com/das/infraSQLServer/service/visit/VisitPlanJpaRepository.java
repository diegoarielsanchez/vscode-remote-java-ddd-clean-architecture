package com.das.infraSQLServer.service.visit;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VisitPlanJpaRepository extends JpaRepository<VisitPlanEntity, String> {

    @Query("SELECT CASE WHEN COUNT(v) > 0 THEN true ELSE false END FROM VisitPlanEntity v " +
           "WHERE v.healthCareProfId = :hcpId " +
           "AND v.medicalSalesRepId = :msrId " +
           "AND v.visitDateTime >= :startOfDay " +
           "AND v.visitDateTime < :endOfDay " +
           "AND v.active = true " +
           "AND (:excludeId IS NULL OR v.id <> :excludeId)")
    boolean existsDuplicateOnDay(
        @Param("startOfDay") LocalDateTime startOfDay,
        @Param("endOfDay") LocalDateTime endOfDay,
        @Param("hcpId") String hcpId,
        @Param("msrId") String msrId,
        @Param("excludeId") String excludeId
    );
}
