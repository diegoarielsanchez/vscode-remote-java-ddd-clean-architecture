package com.das.cleanddd.domain.catalog.reservation.entities;

/**
 * RESERVED -&gt; RELEASED (the order was rejected) or FULFILLED (the order was delivered).
 * REJECTED is final: nothing was held back.
 */
public enum ReservationStatus {
    RESERVED,
    REJECTED,
    RELEASED,
    FULFILLED
}
