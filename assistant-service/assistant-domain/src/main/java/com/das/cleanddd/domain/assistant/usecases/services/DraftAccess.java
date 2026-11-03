package com.das.cleanddd.domain.assistant.usecases.services;

import com.das.cleanddd.domain.assistant.entities.IOrderDraftRepository;
import com.das.cleanddd.domain.assistant.entities.OrderDraft;
import com.das.cleanddd.domain.assistant.entities.OrderDraftId;
import com.das.cleanddd.domain.assistant.usecases.dtos.DraftDecisionInputDTO;

/** Loads a draft for the rep it belongs to; anyone else gets {@link DraftNotFoundException}. */
final class DraftAccess {

    private DraftAccess() {
    }

    static OrderDraft load(IOrderDraftRepository repository, DraftDecisionInputDTO input) throws DraftNotFoundException {
        if (input == null || input.draftId() == null || input.medicalSalesRepId() == null) {
            throw new DraftNotFoundException();
        }
        OrderDraftId id;
        try {
            id = new OrderDraftId(input.draftId());
        } catch (IllegalArgumentException e) {
            throw new DraftNotFoundException();
        }
        return repository.findById(id)
                .filter(draft -> draft.belongsTo(input.medicalSalesRepId()))
                .orElseThrow(DraftNotFoundException::new);
    }
}
