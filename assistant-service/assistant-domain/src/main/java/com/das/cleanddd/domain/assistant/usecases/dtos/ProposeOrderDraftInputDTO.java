package com.das.cleanddd.domain.assistant.usecases.dtos;

import java.util.List;

/**
 * What the assistant proposes. {@code medicalSalesRepId} and {@code conversationId} come from the caller's token
 * and the running conversation, never from the model.
 */
public record ProposeOrderDraftInputDTO(
    String medicalSalesRepId,
    String conversationId,
    List<DraftLineInputDTO> lines,
    String contextHcpId,
    String rationale
) {}
