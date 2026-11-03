package com.das.cleanddd.domain.assistant.entities;

import java.util.Optional;

public interface IOrderDraftRepository {

    void save(OrderDraft draft);

    Optional<OrderDraft> findById(OrderDraftId id);
}
