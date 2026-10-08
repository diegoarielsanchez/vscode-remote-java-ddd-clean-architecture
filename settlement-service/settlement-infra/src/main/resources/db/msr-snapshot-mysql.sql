-- Local MSR status snapshot + idempotent consumer (inbox) for settlement-service (MySQL 8).
-- Required before deploying with SPRING_PROFILES_ACTIVE=prod (ddl-auto=validate); dev creates
-- these tables automatically (ddl-auto=update). Idempotent: safe to run more than once.

CREATE TABLE IF NOT EXISTS msr_snapshot (
    id            VARCHAR(36) NOT NULL PRIMARY KEY,   -- medical sales rep id
    active        BIT(1)      NOT NULL,
    event_version BIGINT      NULL,                   -- last applied msr.* aggregateVersion
    updated_at    DATETIME(6) NOT NULL
);

CREATE TABLE IF NOT EXISTS processed_event (
    event_id     VARCHAR(36)  NOT NULL PRIMARY KEY,   -- integration eventId
    event_type   VARCHAR(100) NOT NULL,
    processed_at DATETIME(6)  NOT NULL
);
