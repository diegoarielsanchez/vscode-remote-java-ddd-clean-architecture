package com.das.infraSQLServer.service.visit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MsrSnapshotJpaRepository extends JpaRepository<MsrSnapshotEntity, String> {}
