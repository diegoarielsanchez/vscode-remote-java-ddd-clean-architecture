package com.das.cleanddd.domain.settlement.entities;

import java.util.List;
import java.util.Optional;

import com.das.cleanddd.domain.shared.criteria.Criteria;

public interface ISettlementRepository {

    void save(Settlement settlement);

    Optional<Settlement> findById(SettlementId id);

    List<Settlement> searchAll();

    List<Settlement> searchAll(int page, int pageSize);

    List<Settlement> matching(Criteria criteria);
}
