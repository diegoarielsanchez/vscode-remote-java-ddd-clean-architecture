package com.das.infra.service.assistant.persistence;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Repository;

import com.das.cleanddd.domain.assistant.entities.IOrderDraftRepository;
import com.das.cleanddd.domain.assistant.entities.OrderDraft;
import com.das.cleanddd.domain.assistant.entities.OrderDraftId;

/** Scaffold store for drafts. To be replaced by a JPA repository before the assistant leaves development. */
@Repository
public class InMemoryOrderDraftRepository implements IOrderDraftRepository, NonDurableStore {

    private final ConcurrentMap<OrderDraftId, OrderDraft> drafts = new ConcurrentHashMap<>();

    @Override
    public void save(OrderDraft draft) {
        drafts.put(draft.id(), draft);
    }

    @Override
    public Optional<OrderDraft> findById(OrderDraftId id) {
        return Optional.ofNullable(drafts.get(id));
    }
}
