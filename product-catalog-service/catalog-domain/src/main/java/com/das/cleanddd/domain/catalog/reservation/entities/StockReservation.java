package com.das.cleanddd.domain.catalog.reservation.entities;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.das.cleanddd.domain.catalog.reservation.events.StockReservationConfirmedEvent;
import com.das.cleanddd.domain.catalog.reservation.events.StockReservationDomainEvent;
import com.das.cleanddd.domain.catalog.reservation.events.StockReservationRejectedEvent;
import com.das.cleanddd.domain.catalog.reservation.events.StockReservationReleasedEvent;
import com.das.cleanddd.domain.shared.AggregateRoot;

/**
 * The catalog's side of the order stock saga: the stock held for one order, identified by the
 * order's id (one reservation per order, which makes reserving idempotent). All or nothing — an
 * order is either fully RESERVED or REJECTED with nothing held. Each transition returns a new
 * immutable instance.
 */
public final class StockReservation extends AggregateRoot<StockReservationDomainEvent> {

    private final String _orderId;
    private final ReservationStatus _status;
    private final List<ReservationLine> _lines;
    private final String _reason;
    private final Instant _createdAt;
    private final Instant _updatedAt;

    public StockReservation(String orderId, ReservationStatus status, List<ReservationLine> lines, String reason,
                            Instant createdAt, Instant updatedAt) {
        this._orderId = Objects.requireNonNull(orderId, "orderId");
        this._status = Objects.requireNonNull(status, "status");
        this._lines = List.copyOf(lines);
        this._reason = reason;
        this._createdAt = createdAt;
        this._updatedAt = updatedAt;
    }

    /** Every line was reserved; {@code lines} carry the name and price snapshot. */
    public static StockReservation reserved(String orderId, List<ReservationLine> lines, Instant now) {
        StockReservation reservation = new StockReservation(orderId, ReservationStatus.RESERVED, lines, null, now, now);
        reservation.record(new StockReservationConfirmedEvent(orderId, reservation._lines));
        return reservation;
    }

    /** Some line could not be reserved; nothing is held. */
    public static StockReservation rejected(String orderId, List<ReservationLine> requested, String reason, Instant now) {
        StockReservation reservation = new StockReservation(orderId, ReservationStatus.REJECTED, requested, reason, now, now);
        reservation.record(new StockReservationRejectedEvent(orderId, reason));
        return reservation;
    }

    /** The order was rejected: give the stock back. Only a RESERVED reservation changes. */
    public StockReservation release(Instant now) {
        if (_status != ReservationStatus.RESERVED) {
            return this;
        }
        StockReservation released = new StockReservation(_orderId, ReservationStatus.RELEASED, _lines, _reason, _createdAt, now);
        released.record(new StockReservationReleasedEvent(_orderId));
        return released;
    }

    /** The order was delivered: the stock is gone for good. Only a RESERVED reservation changes. */
    public StockReservation fulfil(Instant now) {
        if (_status != ReservationStatus.RESERVED) {
            return this;
        }
        return new StockReservation(_orderId, ReservationStatus.FULFILLED, _lines, _reason, _createdAt, now);
    }

    public String orderId() { return _orderId; }
    public ReservationStatus status() { return _status; }
    public List<ReservationLine> lines() { return _lines; }
    public String reason() { return _reason; }
    public Instant createdAt() { return _createdAt; }
    public Instant updatedAt() { return _updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StockReservation other)) return false;
        return _orderId.equals(other._orderId);
    }

    @Override
    public int hashCode() {
        return _orderId.hashCode();
    }
}
