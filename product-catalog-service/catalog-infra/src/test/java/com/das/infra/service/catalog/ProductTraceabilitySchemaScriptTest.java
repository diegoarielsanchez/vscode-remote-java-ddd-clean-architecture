package com.das.infra.service.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import jakarta.persistence.EntityManager;

/**
 * Production runs with {@code ddl-auto=validate}, so upgrading an existing database relies on
 * {@code db/product-traceability-postgresql.sql}. Here the schema comes only from scripts: the products table as it
 * was before, then the upgrade script twice (it must be idempotent). Hibernate then validates every entity against
 * it, and a product written before the upgrade still reads back, with the new fields empty.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:traceabilityschema;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:db/outbox-postgresql.sql,classpath:db/products-before-traceability.sql,"
                + "classpath:db/product-traceability-postgresql.sql,classpath:db/product-traceability-postgresql.sql",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class ProductTraceabilitySchemaScriptTest {

    @Autowired private ProductJpaRepository products;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    @Test
    void upgradedSchemaStoresTheFieldsAndKeepsOlderRows() {
        jdbc.update("INSERT INTO products (id, name, price, unit, stock, active, version) "
                + "VALUES ('old-1', 'Aspirin', 1.00, 'BOX', 10, TRUE, 0)");

        ProductEntity entity = new ProductEntity();
        entity.setId("new-1");
        entity.setName("Insulin 100IU");
        entity.setPrice(new BigDecimal("20.00"));
        entity.setUnit("VIAL");
        entity.setStock(50);
        entity.setActive(true);
        entity.setBatchNumber("L2026-07A");
        entity.setSerialNumber("SN-000123");
        entity.setExpirationDate(LocalDate.of(2027, 3, 31));
        products.save(entity);
        entityManager.flush();
        entityManager.clear();

        ProductEntity back = products.findById("new-1").orElseThrow();
        assertEquals("L2026-07A", back.getBatchNumber());
        assertEquals("SN-000123", back.getSerialNumber());
        assertEquals(LocalDate.of(2027, 3, 31), back.getExpirationDate());

        ProductEntity old = products.findById("old-1").orElseThrow();
        assertNull(old.getBatchNumber());
        assertNull(old.getSerialNumber());
        assertNull(old.getExpirationDate());
    }
}
