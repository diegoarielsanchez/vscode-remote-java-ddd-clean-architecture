package com.das.cleanddd.domain.medicalsalesrep.usecases.dtos;

/** {@code reason}: why the record is changed (required, at most 500 characters); kept in the audit trail. */
public record UpdateMedicalSalesRepInputDTO(
    String id,
    String name,
    String surname,
    String email,
    String reason
    ) {

    /** Without a reason, which the update refuses: kept for callers that predate it. */
    public UpdateMedicalSalesRepInputDTO(String id, String name, String surname, String email) {
        this(id, name, surname, email, null);
    }
}
