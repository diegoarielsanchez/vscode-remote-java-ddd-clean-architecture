-- Storage and transport conditions (GDP) for product-catalog-service (PostgreSQL). Required before deploying this
-- version with SPRING_PROFILES_ACTIVE=prod (ddl-auto=validate); dev adds the columns itself (ddl-auto=update).
-- Idempotent: safe to run more than once. Run after stock-lots-postgresql.sql.

-- The product's conditions from its label. All NULL: none on file (promotional material, or a product recorded
-- before this version — add them with PUT /api/v1/products/update; new lot-tracked products must have them).
ALTER TABLE products ADD COLUMN IF NOT EXISTS storage_min_temp_c         NUMERIC(4,1);  -- °C
ALTER TABLE products ADD COLUMN IF NOT EXISTS storage_max_temp_c         NUMERIC(4,1);  -- °C
ALTER TABLE products ADD COLUMN IF NOT EXISTS storage_max_humidity       INTEGER;       -- % RH; NULL: no limit
ALTER TABLE products ADD COLUMN IF NOT EXISTS storage_protect_from_light BOOLEAN;

-- The conditions at reservation time, which the reserved units must be transported under.
ALTER TABLE stock_reservation_line ADD COLUMN IF NOT EXISTS storage_min_temp_c         NUMERIC(4,1);
ALTER TABLE stock_reservation_line ADD COLUMN IF NOT EXISTS storage_max_temp_c         NUMERIC(4,1);
ALTER TABLE stock_reservation_line ADD COLUMN IF NOT EXISTS storage_max_humidity       INTEGER;
ALTER TABLE stock_reservation_line ADD COLUMN IF NOT EXISTS storage_protect_from_light BOOLEAN;

-- stock_lot.status gains REJECTED: Quality rejected the quarantined lot (e.g. after a temperature excursion). Its
-- units no longer count as on hand and await destruction. The column is VARCHAR(20), so no change is needed.
COMMENT ON COLUMN stock_lot.status IS 'AVAILABLE | QUARANTINED | REJECTED';
