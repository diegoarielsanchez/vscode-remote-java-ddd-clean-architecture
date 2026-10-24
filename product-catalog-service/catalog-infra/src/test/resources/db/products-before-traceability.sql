-- The products table as it was before the traceability fields, as Hibernate created it (test only).
CREATE TABLE IF NOT EXISTS products (
    id          VARCHAR(255) NOT NULL PRIMARY KEY,
    name        VARCHAR(255),
    description VARCHAR(255),
    price       NUMERIC(38, 2),
    unit        VARCHAR(255),
    stock       INTEGER,
    active      BOOLEAN,
    version     BIGINT
);
