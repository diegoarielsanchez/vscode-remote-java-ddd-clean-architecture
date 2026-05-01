package com.das.inframySQL.service.visit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HcpSnapshotJpaRepository extends JpaRepository<HcpSnapshotEntity, String> {}
