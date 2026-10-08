package com.das.infra.service.catalog.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.das.cleanddd.domain.catalog.events.ProductCreatedEvent;
import com.das.cleanddd.domain.catalog.events.ProductDeactivatedEvent;
import com.das.cleanddd.domain.catalog.events.ProductStockReservedEvent;
import com.das.cleanddd.domain.catalog.ports.IProductEventPublisher;
import com.fasterxml.jackson.databind.JsonNode;

@DataJpaTest
@Import(OutboxProductEventPublisher.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class OutboxProductEventPublisherTest {

    private static final Instant NOW = Instant.parse("2026-10-08T09:30:00Z");
    private static final String PRODUCT = "7c3e2a1b-9d8f-4e6a-b5c4-3d2e1f0a9b87";

    @Autowired private OutboxEventJpaRepository outbox;
    @Autowired private AggregateVersionJpaRepository versions;
    @Autowired private IProductEventPublisher springBean;

    private OutboxProductEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxProductEventPublisher(outbox, versions, EventContext.NONE, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void recordsProductAndStockEventsWithIncreasingVersions() throws Exception {
        publisher.publish(new ProductCreatedEvent(PRODUCT, "Amoxicillin 500mg", "Antibiotic", new BigDecimal("12.50"), "BOX", true));
        publisher.publish(new ProductStockReservedEvent(PRODUCT, 5, 95));
        publisher.publish(new ProductDeactivatedEvent(PRODUCT, false));

        List<OutboxEventEntity> rows = outbox.findAll().stream()
                .sorted(Comparator.comparingLong(OutboxEventEntity::getAggregateVersion)).toList();
        assertEquals(List.of("catalog.product.created", "catalog.product.stock-reserved", "catalog.product.deactivated"),
                rows.stream().map(OutboxEventEntity::getEventType).toList());
        assertEquals(List.of(1L, 2L, 3L), rows.stream().map(OutboxEventEntity::getAggregateVersion).toList());

        JsonNode created = OutboxProductEventPublisher.CONTRACT_JSON.readTree(rows.get(0).getPayload());
        assertEquals("product-catalog-service", created.get("producer").asText());
        assertEquals("Amoxicillin 500mg", created.at("/data/name").asText());
        assertEquals(0, new BigDecimal("12.50").compareTo(created.at("/data/price").decimalValue()));

        JsonNode reserved = OutboxProductEventPublisher.CONTRACT_JSON.readTree(rows.get(1).getPayload());
        assertEquals(5, reserved.at("/data/stockDelta").asInt());
        assertEquals(95, reserved.at("/data/remainingStock").asInt());

        JsonNode deactivated = OutboxProductEventPublisher.CONTRACT_JSON.readTree(rows.get(2).getPayload());
        assertEquals(false, deactivated.at("/data/active").asBoolean(true));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void refusesToRunOutsideAUnitOfWork() {
        assertThrows(IllegalTransactionStateException.class,
                () -> springBean.publish(new ProductStockReservedEvent(PRODUCT, 1, 9)));
    }
}
