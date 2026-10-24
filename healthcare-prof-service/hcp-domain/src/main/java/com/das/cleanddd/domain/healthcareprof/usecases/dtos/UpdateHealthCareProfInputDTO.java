package com.das.cleanddd.domain.healthcareprof.usecases.dtos;

import java.util.List;

/** {@code reason}: why the record is changed (required, at most 500 characters); kept in the audit trail. */
public record UpdateHealthCareProfInputDTO(
    String id,
    String name,
    String surname,
    String email,
    List<String> specialties,
    String reason
    ) {

    /** Without a reason, which the update refuses: kept for callers that predate it. */
    public UpdateHealthCareProfInputDTO(String id, String name, String surname, String email, List<String> specialties) {
        this(id, name, surname, email, specialties, null);
    }
}
