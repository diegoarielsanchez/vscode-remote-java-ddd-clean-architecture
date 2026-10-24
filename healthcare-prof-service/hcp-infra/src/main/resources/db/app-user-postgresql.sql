-- Least-privilege database user for healthcare-prof-service (PostgreSQL). Idempotent: run it again to change the password,
-- and after any script that adds tables.
--
-- Run it as the tables' owner (e.g. root), after the other scripts in this folder:
--   psql -U root -d healthcare_db -v ON_ERROR_STOP=1 -v app_password='<secret>' [-v app_user=hcp_user] -f app-user-postgresql.sql
-- Then start the service with PG_USERNAME=hcp_user (or your app_user), PG_PASSWORD=<secret> and ddl-auto=validate
-- (the prod profile's default): this user cannot create or alter tables, so ddl-auto=update would fail.
--
-- The user owns nothing. It can read and change the service's tables, but on audit_event only SELECT and INSERT,
-- so the service can neither change nor delete its audit trail nor drop the guard triggers (ALCOA+).

\if :{?app_user}
\else
\set app_user hcp_user
\endif
\if :{?app_password}
\else
DO $$ BEGIN RAISE EXCEPTION 'Give the password: psql ... -v app_password=<secret>'; END $$;
\endif

SELECT format('CREATE ROLE %I LOGIN', :'app_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'app_user') \gexec
ALTER ROLE :"app_user" WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS
    PASSWORD :'app_password';

SELECT format('GRANT CONNECT ON DATABASE %I TO %I', current_database(), :'app_user') \gexec
GRANT USAGE ON SCHEMA public TO :"app_user";
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO :"app_user";
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO :"app_user";

-- The audit trail: insert and read only.
REVOKE UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER ON audit_event FROM :"app_user";
GRANT SELECT, INSERT ON audit_event TO :"app_user";
