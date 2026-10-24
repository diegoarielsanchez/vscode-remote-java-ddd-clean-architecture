-- Optional traceability fields on products (batch/lot number, serial number, expiration date) for
-- product-catalog-service (PostgreSQL). Required before deploying this version with
-- SPRING_PROFILES_ACTIVE=prod (ddl-auto=validate); dev adds them automatically (ddl-auto=update).
-- Idempotent: safe to run more than once. Existing products keep NULL in all three (not on file).

ALTER TABLE products ADD COLUMN IF NOT EXISTS batch_number    VARCHAR(20);  -- GS1 AI 10, as printed on the pack
ALTER TABLE products ADD COLUMN IF NOT EXISTS serial_number   VARCHAR(20);  -- GS1 AI 21, as printed on the pack
ALTER TABLE products ADD COLUMN IF NOT EXISTS expiration_date DATE;         -- GS1 AI 17; usable through this day

-- A recall starts from a batch number: find every product carrying it.
CREATE INDEX IF NOT EXISTS ix_products_batch_number ON products (batch_number);
