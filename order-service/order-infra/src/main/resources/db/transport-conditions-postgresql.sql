-- Transport under the products' storage conditions (GDP) for order-service (PostgreSQL). Required before deploying
-- this version with SPRING_PROFILES_ACTIVE=prod (ddl-auto=validate); dev adds the columns itself (ddl-auto=update).
-- Idempotent: safe to run more than once.

-- Each line's storage conditions as product-catalog-service reported them when it reserved the stock. All NULL:
-- the product has none (and every line of an order placed before this version).
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS storage_min_temp_c         NUMERIC(4,1);  -- °C
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS storage_max_temp_c         NUMERIC(4,1);  -- °C
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS storage_max_humidity       INTEGER;       -- % RH; NULL: no limit
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS storage_protect_from_light BOOLEAN;

-- The receiver's transport report at delivery. All NULL: delivered before this version, or without conditions.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS transport_conditions_maintained BOOLEAN;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS transport_min_temp_c            NUMERIC(4,1);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS transport_max_temp_c            NUMERIC(4,1);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS transport_max_humidity          INTEGER;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS transport_light_exposed         BOOLEAN;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS transport_note                  VARCHAR(500);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS transport_breaches              VARCHAR(4000); -- one per line

-- Quality's decision on a delivery quarantined for a transport break (status DELIVERY_QUARANTINED, then DELIVERED
-- or DELIVERY_REJECTED).
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivery_decided_by      VARCHAR(255);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivery_decision_reason VARCHAR(500);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivery_decided_at      TIMESTAMP(6) WITH TIME ZONE;
