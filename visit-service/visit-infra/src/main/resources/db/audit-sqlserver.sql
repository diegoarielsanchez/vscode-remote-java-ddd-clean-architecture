-- Audit trail (ALCOA+) for visit-service (SQL Server): every change to a visit or visit plan, kept permanently.
-- Required before deploying with SPRING_PROFILES_ACTIVE=prod (ddl-auto=validate); dev creates the tables
-- automatically (ddl-auto=update) but not the guard below, so run this script there too.
-- Idempotent: safe to run more than once.

IF OBJECT_ID(N'dbo.audit_event', N'U') IS NULL
    CREATE TABLE dbo.audit_event (
        event_id          UNIQUEIDENTIFIER  NOT NULL PRIMARY KEY,
        aggregate_type    VARCHAR(50)       NOT NULL,  -- visit | visit-plan
        aggregate_id      VARCHAR(255)      NOT NULL,
        aggregate_version BIGINT            NOT NULL,  -- per aggregate: a gap means a missing record
        event_type        VARCHAR(100)      NOT NULL,
        producer          VARCHAR(50)       NOT NULL,
        actor             VARCHAR(255)      NULL,      -- authenticated principal; NULL for service-initiated steps
        correlation_id    VARCHAR(64)       NULL,
        occurred_at       DATETIMEOFFSET(6) NOT NULL,
        payload           VARCHAR(8000)     NOT NULL,  -- JSON envelope
        changes           VARCHAR(8000)     NULL,      -- edits: [{"field","from","to"}]
        reason            VARCHAR(500)      NULL,      -- edits: why the record was changed
        CONSTRAINT ux_audit_event_aggregate_version UNIQUE (aggregate_type, aggregate_id, aggregate_version)
    );

-- Added after audit_event was first released.
IF COL_LENGTH(N'dbo.audit_event', N'reason') IS NULL
    ALTER TABLE dbo.audit_event ADD reason VARCHAR(500) NULL;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_audit_event_actor')
    CREATE INDEX ix_audit_event_actor ON dbo.audit_event (actor, occurred_at);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_audit_event_occurred')
    CREATE INDEX ix_audit_event_occurred ON dbo.audit_event (occurred_at);

-- Last entry number per aggregate; its row lock keeps the numbers gap-free under concurrent edits.
IF OBJECT_ID(N'dbo.audit_aggregate_version', N'U') IS NULL
    CREATE TABLE dbo.audit_aggregate_version (
        aggregate_key VARCHAR(300) NOT NULL PRIMARY KEY,  -- "<aggregate type>:<id>"
        last_version  BIGINT       NOT NULL
    );
GO

-- Append-only: UPDATE and DELETE are refused, whoever runs them.
IF OBJECT_ID(N'dbo.trg_audit_event_append_only', N'TR') IS NULL
    EXEC (N'CREATE TRIGGER dbo.trg_audit_event_append_only ON dbo.audit_event
            INSTEAD OF UPDATE, DELETE
          AS
          BEGIN
              THROW 51000, ''audit_event is append-only: UPDATE and DELETE are refused'', 1;
          END');
GO

-- TRUNCATE skips triggers but is refused on a table that a foreign key references: this empty table holds one.
IF OBJECT_ID(N'dbo.audit_event_truncate_guard', N'U') IS NULL
    CREATE TABLE dbo.audit_event_truncate_guard (
        event_id UNIQUEIDENTIFIER NULL
            CONSTRAINT fk_audit_event_truncate_guard REFERENCES dbo.audit_event (event_id)
    );
GO
