package com.das.cleanddd.domain.catalog.reservation.entities;

/** An order that received units of a lot, and where its reservation stands now. */
public record LotRecipient(String orderId, ReservationStatus status, int quantity) {}
