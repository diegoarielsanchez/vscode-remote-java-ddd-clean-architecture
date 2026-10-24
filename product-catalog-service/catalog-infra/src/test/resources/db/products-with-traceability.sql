-- The products table after the product-level traceability release (batch/serial/expiration on the product), with
-- one product whose batch and expiry were filled in (test only). Run after products-before-lots.sql.
ALTER TABLE products ADD COLUMN IF NOT EXISTS batch_number    VARCHAR(20);
ALTER TABLE products ADD COLUMN IF NOT EXISTS serial_number   VARCHAR(20);
ALTER TABLE products ADD COLUMN IF NOT EXISTS expiration_date DATE;
UPDATE products SET batch_number = 'L2026-07A', expiration_date = DATE '2027-03-31'
WHERE id = '11111111-1111-4111-8111-111111111111';
