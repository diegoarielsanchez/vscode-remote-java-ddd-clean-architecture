package com.das.cleanddd.domain.assistant.usecases.dtos;

import java.time.Instant;

import com.das.cleanddd.domain.assistant.entities.DraftStatus;
import com.das.cleanddd.domain.assistant.entities.OrderDraft;

public class OrderDraftMapper {

    public OrderDraftOutputDTO outputFromEntity(OrderDraft draft, Instant now) {
        DraftStatus status = draft.statusAt(now);
        return new OrderDraftOutputDTO(
                draft.id().value(),
                status.name(),
                draft.lines().stream()
                        .map(l -> new OrderDraftOutputDTO.Line(l.productId(), l.gtin().value(), l.name(), l.quantity()))
                        .toList(),
                draft.checks().stream()
                        .map(c -> new OrderDraftOutputDTO.Check(c.rule(), c.productId(), c.outcome().name(), c.message()))
                        .toList(),
                status == DraftStatus.DRAFTED && !draft.hasFailedCheck(),
                draft.contextHcpId(),
                draft.rationale(),
                draft.createdAt(),
                draft.expiresAt(),
                draft.orderId());
    }
}
