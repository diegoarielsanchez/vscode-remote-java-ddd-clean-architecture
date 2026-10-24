-- The products table before stock lots, as Hibernate created it, with some products (test only).
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
INSERT INTO products (id, name, price, unit, stock, active, version)
VALUES ('11111111-1111-4111-8111-111111111111', 'Aspirin', 1.00, 'BOX', 10, TRUE, 0),
       ('22222222-2222-4222-8222-222222222222', 'Out of stock', 1.00, 'BOX', 0, TRUE, 0);
