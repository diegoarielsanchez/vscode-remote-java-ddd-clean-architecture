package com.das.infra.service.order.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import com.das.cleanddd.domain.order.events.OrderCreatedEvent;
import com.das.cleanddd.domain.order.events.OrderSubmittedForApprovalEvent;

import jakarta.persistence.EntityManager;

/**
 * Production runs with {@code ddl-auto=validate}, so the shipped DDL script must contain every
 * column the outbox entities use. The schema here comes <em>only</em> from the script
 * ({@code ddl-auto=none}, H2 in PostgreSQL mode); every outbox operation is then executed against it.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:outboxschema;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:db/outbox-postgresql.sql",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class OutboxSchemaScriptTest {

    private static final Instant NOW = Instant.parse("2026-10-08T09:30:00Z");

    @Autowired private OutboxEventJpaRepository outbox;
    @Autowired private AggregateVersionJpaRepository versions;
    @Autowired private EntityManager entityManager;

    @Test
    void everyOutboxOperationWorksOnTheScriptedSchema() {
        OutboxOrderEventPublisher publisher = new OutboxOrderEventPublisher(outbox, versions, EventContext.NONE,
                Clock.fixed(NOW, ZoneOffset.UTC));
        publisher.publish(new OrderCreatedEvent("8a6e0804-2bd0-4672-b79d-d97027f9071a", "5b1d6c2e-0f4a-4e7b-9c3d-2a8f6e1b7c90",
                java.util.List.of(new OrderCreatedEvent.Line("0d4c8a3e-6b1f-4f27-9e5a-3c2b1a0f9e8d", 1))));
        publisher.publish(new OrderSubmittedForApprovalEvent("8a6e0804-2bd0-4672-b79d-d97027f9071a", new java.math.BigDecimal("10.00")));
        entityManager.flush();
        entityManager.clear();

        var pending = outbox.lockPending(PageRequest.of(0, 10));
        assertEquals(2, pending.size());
        assertEquals(2, pending.get(1).getAggregateVersion());

        pending.get(0).recordFailure("nack: test");
        pending.get(0).markPublished(NOW.minus(Duration.ofDays(30)));
        entityManager.flush();

        assertEquals(1, outbox.deletePublishedBefore(NOW.minus(Duration.ofDays(7))));
        assertEquals(1, outbox.countByPublishedAtIsNull());
    }
}
