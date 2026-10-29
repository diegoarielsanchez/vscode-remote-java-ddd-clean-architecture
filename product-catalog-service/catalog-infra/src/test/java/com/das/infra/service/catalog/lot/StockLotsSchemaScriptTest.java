package com.das.infra.service.catalog.lot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.das.cleanddd.domain.catalog.lot.LotAllocation;
import com.das.cleanddd.domain.catalog.reservation.entities.LotRecipient;
import com.das.cleanddd.domain.catalog.reservation.entities.ReservationLine;
import com.das.cleanddd.domain.catalog.reservation.entities.ReservationStatus;
import com.das.cleanddd.domain.catalog.reservation.entities.StockReservation;
import com.das.infra.service.catalog.reservation.SQLStockReservationRepository;

import jakarta.persistence.EntityManager;

/**
 * Production runs with {@code ddl-auto=validate}, so upgrading relies on {@code db/stock-lots-postgresql.sql}. The
 * schema here comes only from scripts (H2 in PostgreSQL mode): the database as it was, then the upgrade script twice
 * (it must be idempotent). Hibernate then validates every entity against the result.
 */
class StockLotsSchemaScriptTest {

    private static final String ASPIRIN = "11111111-1111-4111-8111-111111111111";
    private static final String OUT_OF_STOCK = "22222222-2222-4222-8222-222222222222";

    @Nested
    @DataJpaTest
    @AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
    @Import(SQLStockReservationRepository.class)
    @TestPropertySource(properties = {
            "spring.datasource.url=jdbc:h2:mem:lotsfromstock;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.sql.init.mode=always",
            "spring.sql.init.schema-locations=classpath:db/outbox-postgresql.sql,classpath:db/products-before-lots.sql,"
                    + "classpath:db/stock-lots-postgresql.sql,classpath:db/stock-lots-postgresql.sql,"
                    + "classpath:db/storage-conditions-postgresql.sql,classpath:db/storage-conditions-postgresql.sql",
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
    })
    class FromPlainStock {

        @Autowired private StockLotJpaRepository lots;
        @Autowired private SQLStockReservationRepository reservations;
        @Autowired private JdbcTemplate jdbc;
        @Autowired private EntityManager entityManager;

        @Test
        void existingStockBecomesOneLegacyLotPerProduct() {
            List<StockLotEntity> aspirin = lots.findByProductId(ASPIRIN);
            assertEquals(1, aspirin.size(), "run twice, migrated once");
            assertEquals("LEGACY", aspirin.get(0).getBatchNumber());
            assertNull(aspirin.get(0).getExpirationDate(), "unknown: to be reviewed");
            assertEquals(10, aspirin.get(0).getQuantity());
            assertEquals(List.of(), lots.findByProductId(OUT_OF_STOCK));
            assertFalse(jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name = 'products'",
                    String.class).contains("batch_number"), "the product-level columns are gone");
            assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT lot_tracked FROM products WHERE id = ?", Boolean.class,
                    ASPIRIN), "existing products are lot-tracked medicines");
        }

        @Test
        void reservationAllocationsAreStoredAndFindTheirRecipients() {
            String order = "33333333-3333-4333-8333-333333333333";
            LotAllocation a = new LotAllocation("legacy-" + ASPIRIN, "LEGACY", null, 4);
            reservations.save(StockReservation.reserved(order,
                    List.of(new ReservationLine(ASPIRIN, 4, "Aspirin", new BigDecimal("1.00"), List.of(a))),
                    Instant.parse("2026-10-24T12:00:00Z")));
            entityManager.flush();
            entityManager.clear();

            assertEquals(List.of(a), reservations.findByOrderId(order).orElseThrow().lines().get(0).allocations());
            assertEquals(List.of(new LotRecipient(order, ReservationStatus.RESERVED, 4)),
                    reservations.findRecipientsOfLot("legacy-" + ASPIRIN));
        }
    }

    @Nested
    @DataJpaTest
    @AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
    @TestPropertySource(properties = {
            "spring.datasource.url=jdbc:h2:mem:lotsfromtraceability;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.sql.init.mode=always",
            "spring.sql.init.schema-locations=classpath:db/outbox-postgresql.sql,classpath:db/products-before-lots.sql,"
                    + "classpath:db/products-with-traceability.sql,classpath:db/stock-lots-postgresql.sql,"
                    + "classpath:db/stock-lots-postgresql.sql,classpath:db/storage-conditions-postgresql.sql",
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
    })
    class FromProductLevelTraceability {

        @Autowired private StockLotJpaRepository lots;

        @Test
        void theProductsBatchAndExpiryMoveIntoItsLot() {
            StockLotEntity lot = lots.findByProductId(ASPIRIN).get(0);
            assertEquals("L2026-07A", lot.getBatchNumber());
            assertEquals(LocalDate.of(2027, 3, 31), lot.getExpirationDate());
            assertEquals(10, lot.getQuantity());
        }
    }
}
