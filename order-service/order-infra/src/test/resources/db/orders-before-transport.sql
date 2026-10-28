-- The orders tables as Hibernate created them before transport conditions (for TransportSchemaScriptTest).
CREATE TABLE IF NOT EXISTS orders (
    id                   VARCHAR(255) NOT NULL PRIMARY KEY,
    medical_sales_rep_id VARCHAR(255),
    status               VARCHAR(255),
    approved_by          VARCHAR(255),
    rejected_by          VARCHAR(255),
    rejection_reason     VARCHAR(255),
    created_at           TIMESTAMP(6) WITH TIME ZONE,
    approved_at          TIMESTAMP(6) WITH TIME ZONE,
    rejected_at          TIMESTAMP(6) WITH TIME ZONE,
    delivered_at         TIMESTAMP(6) WITH TIME ZONE
);
CREATE TABLE IF NOT EXISTS order_lines (
    id                    VARCHAR(255) NOT NULL PRIMARY KEY,
    product_id            VARCHAR(255),
    product_name_snapshot VARCHAR(255),
    quantity              INTEGER,
    unit_price            NUMERIC(38,2),
    order_id              VARCHAR(255) NOT NULL REFERENCES orders (id)
);
