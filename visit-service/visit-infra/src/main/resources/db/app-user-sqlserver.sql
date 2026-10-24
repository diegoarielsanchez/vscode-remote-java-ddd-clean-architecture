-- Least-privilege database user for visit-service (SQL Server). Idempotent: run it again to change the password.
--
-- Run it with sqlcmd as an administrator (e.g. sa), after the other scripts in this folder:
--   sqlcmd -S localhost -U sa -P '<sa password>' -No -d visitdb -v APP_LOGIN=visit_user APP_PASSWORD='<secret>' -i app-user-sqlserver.sql
-- The password must not contain a single quote ('). sqlcmd shows variables in the process list, so prefer
-- running it from a shell that keeps no history, or change the password afterwards.
-- Then start the service with DB_USERNAME=visit_user, DB_PASSWORD=<secret> and ddl-auto=validate (the prod
-- profile's default): this user cannot create or alter tables, so ddl-auto=update would fail.
--
-- The user can read and change the service's tables but has only SELECT and INSERT on dbo.audit_event. It has no
-- ALTER or CONTROL permission, so it can't disable the guard trigger, drop it or truncate the table (ALCOA+).

IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = N'$(APP_LOGIN)')
    EXEC (N'CREATE LOGIN [$(APP_LOGIN)] WITH PASSWORD = N''$(APP_PASSWORD)'', CHECK_POLICY = ON')
ELSE
    EXEC (N'ALTER LOGIN [$(APP_LOGIN)] WITH PASSWORD = N''$(APP_PASSWORD)''');
GO

IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'$(APP_LOGIN)')
    EXEC (N'CREATE USER [$(APP_LOGIN)] FOR LOGIN [$(APP_LOGIN)] WITH DEFAULT_SCHEMA = dbo');
GO

GRANT SELECT, INSERT, UPDATE, DELETE ON SCHEMA::dbo TO [$(APP_LOGIN)];
-- The audit trail: insert and read only. DENY wins over the schema-wide GRANT.
DENY UPDATE, DELETE, ALTER, CONTROL, TAKE OWNERSHIP ON OBJECT::dbo.audit_event TO [$(APP_LOGIN)];
DENY ALTER, CONTROL ON OBJECT::dbo.audit_event_truncate_guard TO [$(APP_LOGIN)];
GO
