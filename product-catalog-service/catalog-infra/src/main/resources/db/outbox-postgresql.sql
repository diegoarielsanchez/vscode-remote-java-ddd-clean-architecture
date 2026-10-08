-- Transactional outbox for product-catalog-service (PostgreSQL).
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
