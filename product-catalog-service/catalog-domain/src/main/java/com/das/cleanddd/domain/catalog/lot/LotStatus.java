package com.das.cleanddd.domain.catalog.lot;

/** Whether a lot may be delivered. Quarantined stock stays on hand but is never allocated to an order; rejected stock is no longer on hand. */
public enum LotStatus {
    AVAILABLE,
    /** Blocked, e.g. during a recall, a quality investigation or a temperature excursion, until the quarantine is lifted. */
    QUARANTINED,
    /**
     * Quality decided the units cannot be used (e.g. after an excursion). Final: the units are no longer on hand and
     * await destruction; the quantity is kept for reconciliation.
     */
    REJECTED
}
