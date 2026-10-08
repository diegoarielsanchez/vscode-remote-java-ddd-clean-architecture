-- Idempotent consumer (inbox) + event ordering for visit-service (SQL Server).
-- Required before deploying with SPRING_PROFILES_ACTIVE=prod (ddl-auto=validate); dev creates
-- these automatically (ddl-auto=update). Idempotent: safe to run more than once.

IF OBJECT_ID(N'dbo.processed_event', N'U') IS NULL
    CREATE TABLE dbo.processed_event (
        event_id     UNIQUEIDENTIFIER  NOT NULL PRIMARY KEY,  -- integration eventId
        event_type   VARCHAR(100)      NOT NULL,
        processed_at DATETIMEOFFSET(6) NOT NULL
    );

IF COL_LENGTH(N'dbo.hcp_snapshot', N'event_version') IS NULL
    ALTER TABLE dbo.hcp_snapshot ADD event_version BIGINT NULL;  -- last applied hcp.* aggregateVersion

IF COL_LENGTH(N'dbo.msr_snapshot', N'event_version') IS NULL
    ALTER TABLE dbo.msr_snapshot ADD event_version BIGINT NULL;  -- last applied msr.* aggregateVersion
