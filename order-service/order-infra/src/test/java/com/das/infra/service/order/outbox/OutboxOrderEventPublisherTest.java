package com.das.infra.service.order.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

import com.das.cleanddd.domain.order.events.OrderCreatedEvent;
import com.das.cleanddd.domain.order.events.OrderRejectedEvent;
import com.das.cleanddd.domain.order.events.OrderSubmittedForApprovalEvent;
import com.das.cleanddd.domain.order.ports.IOrderEventPublisher;
import com.fasterxml.jackson.databind.JsonNode;

@DataJpaTest
@Import(OutboxOrderEventPublisher.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class OutboxOrderEventPublisherTest {

    private static final Instant NOW = Instant.parse("2026-10-08T09:30:00Z");
    private static final String ORDER = "0d6f6b5e-3f0e-4c6a-9b8e-6c2a1f4d7e90";
    private static final String MSR = "5b1d6c2e-0f4a-4e7b-9c3d-2a8f6e1b7c90";
    private static final String PRODUCT_A = "1f0e2d3c-4b5a-4968-8776-655443322110";
    private static final String PRODUCT_B = "2a1b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d";

    @Autowired private OutboxEventJpaRepository outbox;
    @Autowired private AggregateVersionJpaRepository versions;
    @Autowired private IOrderEventPublisher springBean;

    private OutboxOrderEventPublisher publisher;

    @BeforeEach
    void setUp() {
        EventContext context = new EventContext() {
            @Override public String correlationId() { return "trace-123"; }
            @Override public String actor() { return "rep.manager"; }
        };
        publisher = new OutboxOrderEventPublisher(outbox, versions, context, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void recordsTheOrderLifecycleWithIncreasingVersions() throws Exception {
        publisher.publish(new OrderCreatedEvent(ORDER, MSR, List.of(
                new OrderCreatedEvent.Line(PRODUCT_A, 3), new OrderCreatedEvent.Line(PRODUCT_B, 1))));
        publisher.publish(new OrderSubmittedForApprovalEvent(ORDER, new BigDecimal("125.50")));
        publisher.publish(new OrderRejectedEvent(ORDER, "approver", "Out of budget"));

        List<OutboxEventEntity> rows = outbox.findAll().stream()
                .sorted(Comparator.comparingLong(OutboxEventEntity::getAggregateVersion)).toList();
        assertEquals(List.of("order.created", "order.submitted-for-approval", "order.rejected"),
                rows.stream().map(OutboxEventEntity::getEventType).toList());
        assertEquals(List.of(1L, 2L, 3L), rows.stream().map(OutboxEventEntity::getAggregateVersion).toList());

        JsonNode created = OutboxOrderEventPublisher.CONTRACT_JSON.readTree(rows.get(0).getPayload());
        assertEquals("order-service", created.get("producer").asText());
        assertEquals("rep.manager", created.get("actor").asText());
        assertEquals(MSR, created.at("/data/medicalSalesRepId").asText());
        assertEquals(2, created.at("/data/lineCount").asInt());
        assertEquals(PRODUCT_A, created.at("/data/lines/0/productId").asText());
        assertEquals(3, created.at("/data/lines/0/quantity").asInt());
        assertEquals(1, created.at("/data/lines/1/quantity").asInt());
        assertTrue(created.at("/data/totalAmount").isMissingNode(), "not priced yet: left out");

        JsonNode submitted = OutboxOrderEventPublisher.CONTRACT_JSON.readTree(rows.get(1).getPayload());
        assertEquals(0, new BigDecimal("125.50").compareTo(submitted.at("/data/totalAmount").decimalValue()));

        JsonNode rejected = OutboxOrderEventPublisher.CONTRACT_JSON.readTree(rows.get(2).getPayload());
        assertEquals("approver", rejected.at("/data/decidedBy").asText());
        assertEquals("Out of budget", rejected.at("/data/reason").asText());
        assertTrue(rejected.at("/data/totalAmount").isMissingNode());
        assertTrue(rejected.at("/data/lines").isMissingNode());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void refusesToRunOutsideAUnitOfWork() {
        assertThrows(IllegalTransactionStateException.class,
                () -> springBean.publish(new OrderSubmittedForApprovalEvent(ORDER, BigDecimal.TEN)));
    }
}
