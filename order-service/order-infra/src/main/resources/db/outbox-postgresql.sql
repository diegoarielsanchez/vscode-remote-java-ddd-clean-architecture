-- Transactional outbox for order-service (PostgreSQL).
-- Required before deploying with SPRING_PROFILES_ACTIVE=prod (ddl-auto=validate); dev creates
-- these tables automatically (ddl-auto=update). Idempotent: safe to run more than once.

CREATE TABLE IF NOT EXISTS outbox_event (
    id                UUID                        PRIMARY KEY,   -- = integration eventId
    aggregate_type    VARCHAR(50)                 NOT NULL,
    aggregate_id      VARCHAR(64)                 NOT NULL,
    aggregate_version BIGINT                      NOT NULL,
    event_type        VARCHAR(100)                NOT NULL,
    payload           VARCHAR(8000)               NOT NULL,      -- JSON envelope
    occurred_at       TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    published_at      TIMESTAMP(6) WITH TIME ZONE,
    attempts          INTEGER                     NOT NULL DEFAULT 0,
    last_error        VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS ix_outbox_event_pending ON outbox_event (published_at, occurred_at);

CREATE TABLE IF NOT EXISTS outbox_aggregate_version (
    aggregate_key VARCHAR(120) PRIMARY KEY,                     -- "<aggregate type>:<id>"
    last_version  BIGINT       NOT NULL
);

-- Inbox for the stock saga: catalog.reservation.* events already applied (idempotent consumer).
CREATE TABLE IF NOT EXISTS processed_event (
    event_id     VARCHAR(36)                 PRIMARY KEY,
    event_type   VARCHAR(100)                NOT NULL,
    processed_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

-- Audit trail (ALCOA+): every integration event, kept permanently. Written in the same transaction as the change,
-- next to outbox_event; unlike outbox_event it is never purged. Run audit-postgresql.sql after this script to make
-- the table append-only in the database and copy in the events still held in outbox_event.
CREATE TABLE IF NOT EXISTS audit_event (
    event_id          UUID                        PRIMARY KEY,   -- = integration eventId
    aggregate_type    VARCHAR(50)                 NOT NULL,
    aggregate_id      VARCHAR(64)                 NOT NULL,
    aggregate_version BIGINT                      NOT NULL,      -- per aggregate: a gap means a missing record
    event_type        VARCHAR(100)                NOT NULL,
    producer          VARCHAR(50)                 NOT NULL,
    actor             VARCHAR(255),                              -- authenticated principal; NULL for service-initiated steps
    correlation_id    VARCHAR(64),
    occurred_at       TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    payload           VARCHAR(8000)               NOT NULL,      -- JSON envelope, exactly as published
    changes           VARCHAR(8000),                             -- edits: [{"field","from","to"}], incl. unpublished fields
    CONSTRAINT ux_audit_event_aggregate_version UNIQUE (aggregate_type, aggregate_id, aggregate_version)
);
-- Added after audit_event was first released.
ALTER TABLE audit_event ADD COLUMN IF NOT EXISTS changes VARCHAR(8000);
CREATE INDEX IF NOT EXISTS ix_audit_event_actor ON audit_event (actor, occurred_at);
CREATE INDEX IF NOT EXISTS ix_audit_event_occurred ON audit_event (occurred_at);
