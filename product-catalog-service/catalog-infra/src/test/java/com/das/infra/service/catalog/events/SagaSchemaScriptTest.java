package com.das.infra.service.catalog.events;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.das.cleanddd.domain.catalog.reservation.entities.ReservationLine;
import com.das.cleanddd.domain.catalog.reservation.entities.ReservationStatus;
import com.das.cleanddd.domain.catalog.reservation.entities.StockReservation;
import com.das.infra.service.catalog.reservation.SQLStockReservationRepository;

import jakarta.persistence.EntityManager;

/**
 * Production runs with {@code ddl-auto=validate}: the shipped DDL script must contain every column
 * the saga's entities use. The schema comes <em>only</em> from the script here.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(SQLStockReservationRepository.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:sagaschema;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:db/outbox-postgresql.sql",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class SagaSchemaScriptTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-08T09:30:00Z"), ZoneOffset.UTC);

    @Autowired private SQLStockReservationRepository reservations;
    @Autowired private ProcessedEventJpaRepository processed;
    @Autowired private EntityManager entityManager;

    @Test
    void reservationsAndProcessedEventsWorkOnTheScriptedSchema() {
        String order = UUID.randomUUID().toString();
        StockReservation reserved = StockReservation.reserved(order, List.of(
                new ReservationLine(UUID.randomUUID().toString(), 4, "Amoxicillin 500mg", new BigDecimal("12.50")),
                new ReservationLine(UUID.randomUUID().toString(), 1, "Ibuprofen 400mg", new BigDecimal("3.00"))),
                CLOCK.instant());
        reservations.save(reserved);
        processed.save(new ProcessedEventEntity(UUID.randomUUID().toString(), "order.created", CLOCK.instant()));
        entityManager.flush();
        entityManager.clear();

        reservations.save(reservations.findByOrderId(order).orElseThrow().release(CLOCK.instant()));
        entityManager.flush();
        entityManager.clear();

        StockReservation reloaded = reservations.findByOrderId(order).orElseThrow();
        assertEquals(ReservationStatus.RELEASED, reloaded.status());
        assertEquals(reserved.lines(), reloaded.lines());
        assertEquals(1, processed.count());
    }
}
