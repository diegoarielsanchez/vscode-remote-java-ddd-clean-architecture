package com.das.cleanddd.domain.assistant.usecases.dtos;

/**
 * @param medicalSalesRepId from the caller's token ({@code msr_id})
 * @param conversationId    {@code null} to start a new conversation
 * @param visitId           a visit the rep is asking about, or {@code null}
 */
public record AskAssistantInputDTO(
    String medicalSalesRepId,
    String conversationId,
    String text,
    String visitId
) {}
