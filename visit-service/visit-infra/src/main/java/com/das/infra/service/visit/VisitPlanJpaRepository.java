package com.das.infra.service.visit;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface VisitPlanJpaRepository extends JpaRepository<VisitPlanEntity, String> {

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE VisitPlanEntity v SET v.active = false " +
           "WHERE v.healthCareProfId = :hcpId " +
           "AND v.visitDateTime > :now " +
           "AND v.active = true")
    void deactivateFutureByHealthCareProfId(
        @Param("hcpId") String hcpId,
        @Param("now") LocalDateTime now
    );

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE VisitPlanEntity v SET v.active = false " +
           "WHERE v.medicalSalesRepId = :msrId " +
           "AND v.visitDateTime > :now " +
           "AND v.active = true")
    void deactivateFutureByMedicalSalesRepId(
        @Param("msrId") String msrId,
        @Param("now") LocalDateTime now
    );

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
