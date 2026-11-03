package com.das.cleanddd.domain.assistant.usecases.dtos;

/** @param medicalSalesRepId from the caller's token ({@code msr_id}) */
public record DraftDecisionInputDTO(
    String medicalSalesRepId,
    String draftId
) {}
