package com.das.cleanddd.domain.catalog.usecases.dtos;

/** An order that received units of a lot: what a recall needs to reach its MSR and HCP. */
public record LotRecipientOutputDTO(
    String orderId,
    String reservationStatus,
    int quantity
) {}
