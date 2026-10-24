-- Stock held in lots (GS1 batch + expiration date) and the product GTIN, for product-catalog-service
-- (PostgreSQL). Required before deploying this version with SPRING_PROFILES_ACTIVE=prod
-- (ddl-auto=validate). Also run it once on a dev database that already has products: ddl-auto=update
-- creates the new tables but does not move existing stock into lots.
-- Idempotent: safe to run more than once. Run after outbox-postgresql.sql.
--
-- Replaces product-traceability-postgresql.sql (batch/serial/expiration on the product itself); works
-- whether or not that script was run.

-- GS1 GTIN (AI 01), 14 digits. One GTIN identifies one trade item: unique (NULLs allowed).
ALTER TABLE products ADD COLUMN IF NOT EXISTS gtin VARCHAR(14);
CREATE UNIQUE INDEX IF NOT EXISTS ux_products_gtin ON products (gtin);

-- One row per product and batch (GS1 identifies a lot by GTIN + batch number).
CREATE TABLE IF NOT EXISTS stock_lot (
    id              VARCHAR(64)                 NOT NULL PRIMARY KEY,
    product_id      VARCHAR(36)                 NOT NULL,
    batch_number    VARCHAR(20)                 NOT NULL,   -- GS1 AI 10, exactly as printed
    expiration_date DATE,                                   -- GS1 AI 17; NULL only for migrated stock
    quantity        INTEGER                     NOT NULL CHECK (quantity >= 0),
    status          VARCHAR(20)                 NOT NULL,   -- AVAILABLE | QUARANTINED
    received_at     TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version         BIGINT,
    CONSTRAINT ux_stock_lot_product_batch UNIQUE (product_id, batch_number)
);
-- First expiry, first out: a product's lots by expiration date.
CREATE INDEX IF NOT EXISTS ix_stock_lot_product_expiry ON stock_lot (product_id, expiration_date);
-- Recall starts from a batch number.
CREATE INDEX IF NOT EXISTS ix_stock_lot_batch ON stock_lot (batch_number);

-- Which lots each reservation line was served from (recall: who received a lot).
CREATE TABLE IF NOT EXISTS stock_reservation_allocation (
    order_id        VARCHAR(36) NOT NULL REFERENCES stock_reservation (order_id),
    allocation_no   INTEGER     NOT NULL,
    line_no         INTEGER     NOT NULL,
    lot_id          VARCHAR(64) NOT NULL,
    batch_number    VARCHAR(20) NOT NULL,
    expiration_date DATE,
    quantity        INTEGER     NOT NULL,
    PRIMARY KEY (order_id, allocation_no)
);
CREATE INDEX IF NOT EXISTS ix_stock_reservation_allocation_lot ON stock_reservation_allocation (lot_id);

-- Move existing stock into lots: each product that has stock and no lot yet gets one lot holding it
-- (so run this before receiving stock with this version).
-- Its batch and expiry come from the previous release's product columns when they were filled in;
-- otherwise the batch is 'LEGACY' and the expiry unknown (NULL) — review those lots and quarantine
-- any that cannot be identified. The ADD COLUMNs only make the copy work if that release's script
-- never ran; the columns are dropped right after.
ALTER TABLE products ADD COLUMN IF NOT EXISTS stock           INTEGER;      -- absent on a database created by this version
ALTER TABLE products ADD COLUMN IF NOT EXISTS batch_number    VARCHAR(20);
ALTER TABLE products ADD COLUMN IF NOT EXISTS serial_number   VARCHAR(20);
ALTER TABLE products ADD COLUMN IF NOT EXISTS expiration_date DATE;

INSERT INTO stock_lot (id, product_id, batch_number, expiration_date, quantity, status, received_at, version)
SELECT 'legacy-' || p.id, p.id, COALESCE(p.batch_number, 'LEGACY'), p.expiration_date, p.stock, 'AVAILABLE',
       CURRENT_TIMESTAMP, 0
FROM products p
WHERE p.stock > 0
  AND NOT EXISTS (SELECT 1 FROM stock_lot l WHERE l.product_id = p.id);

-- Stock now lives only in stock_lot. products.stock is kept (no longer written) for a rollback.
ALTER TABLE products ALTER COLUMN stock DROP NOT NULL;
ALTER TABLE products DROP COLUMN IF EXISTS batch_number;
ALTER TABLE products DROP COLUMN IF EXISTS serial_number;
ALTER TABLE products DROP COLUMN IF EXISTS expiration_date;
