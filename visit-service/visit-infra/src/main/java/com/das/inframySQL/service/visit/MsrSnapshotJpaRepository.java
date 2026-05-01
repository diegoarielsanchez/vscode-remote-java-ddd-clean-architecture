package com.das.inframySQL.service.visit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MsrSnapshotJpaRepository extends JpaRepository<MsrSnapshotEntity, String> {}
