package com.das.cleanddd.domain.catalog.lot;

/** Whether a lot may be delivered. Quarantined stock stays on hand but is never allocated to an order. */
public enum LotStatus {
    AVAILABLE,
    /** Blocked, e.g. during a recall or a quality investigation, until the quarantine is lifted. */
    QUARANTINED
}
