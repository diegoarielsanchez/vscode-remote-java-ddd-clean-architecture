package com.das.cleanddd.domain.assistant.usecases.dtos;

/** @param draftId the draft proposed in this turn, or {@code null} */
public record AssistantReplyDTO(
    String conversationId,
    String text,
    String draftId
) {}
