-- Audit trail guard for healthcare-prof-service (PostgreSQL only). Run after outbox-postgresql.sql, which creates audit_event.
-- Idempotent: safe to run more than once.
--
-- 1. Copies the events still held in outbox_event (the last few days) into audit_event, so the trail starts
--    before this version was deployed.
-- 2. Makes audit_event append-only: UPDATE, DELETE and TRUNCATE are refused with an error.
--    The table owner can still drop the triggers. For real enforcement run the service as a role that does not
--    own the table, granted only SELECT and INSERT on it.

INSERT INTO audit_event (event_id, aggregate_type, aggregate_id, aggregate_version, event_type, producer, actor,
                         correlation_id, occurred_at, payload)
SELECT o.id, o.aggregate_type, o.aggregate_id, o.aggregate_version, o.event_type,
       COALESCE(o.payload::json ->> 'producer', 'healthcare-prof-service'), o.payload::json ->> 'actor',
       o.payload::json ->> 'correlationId', o.occurred_at, o.payload
FROM outbox_event o
WHERE NOT EXISTS (SELECT 1 FROM audit_event a WHERE a.event_id = o.id)
ON CONFLICT DO NOTHING;

CREATE OR REPLACE FUNCTION audit_event_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_event is append-only: % refused', TG_OP;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS audit_event_no_change ON audit_event;
CREATE TRIGGER audit_event_no_change BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION audit_event_append_only();

DROP TRIGGER IF EXISTS audit_event_no_truncate ON audit_event;
CREATE TRIGGER audit_event_no_truncate BEFORE TRUNCATE ON audit_event
    FOR EACH STATEMENT EXECUTE FUNCTION audit_event_append_only();
