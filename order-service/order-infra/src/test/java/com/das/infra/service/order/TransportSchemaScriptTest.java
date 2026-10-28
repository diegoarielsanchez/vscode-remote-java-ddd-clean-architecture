package com.das.infra.service.order;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.das.cleanddd.domain.order.entities.DeliveryCheck;
import com.das.cleanddd.domain.order.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.order.entities.Order;
import com.das.cleanddd.domain.order.entities.OrderLine;
import com.das.cleanddd.domain.order.entities.OrderLineQuantity;
import com.das.cleanddd.domain.order.entities.OrderLineUnitPrice;
import com.das.cleanddd.domain.order.entities.OrderStatus;
import com.das.cleanddd.domain.order.entities.ProductId;
import com.das.cleanddd.domain.order.entities.ReservedLinePrice;
import com.das.cleanddd.domain.shared.storage.ConditionReadings;
import com.das.cleanddd.domain.shared.storage.StorageCondition;

import jakarta.persistence.EntityManager;

/**
 * Production runs with {@code ddl-auto=validate}, so upgrading relies on {@code db/transport-conditions-postgresql.sql}.
 * The schema here comes only from scripts (H2 in PostgreSQL mode): the orders tables as they were, then the upgrade
 * script twice (it must be idempotent). Hibernate validates every entity against the result, and an order with a
 * quarantined, then released, delivery round-trips through it.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(SQLOrderRepository.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:transportschema;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:db/outbox-postgresql.sql,classpath:db/orders-before-transport.sql,"
                + "classpath:db/transport-conditions-postgresql.sql,classpath:db/transport-conditions-postgresql.sql",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class TransportSchemaScriptTest {

    @Autowired private SQLOrderRepository repository;
    @Autowired private EntityManager entityManager;

    @Test
    void anOrderWithItsConditionsTransportReportAndDecisionRoundTrips() throws Exception {
        StorageCondition fridge = new StorageCondition(new BigDecimal("2"), new BigDecimal("8"), 60, true);
        ProductId insulin = new ProductId(UUID.randomUUID().toString());
        Order order = Order.create(new MedicalSalesRepId(UUID.randomUUID().toString()),
                        List.of(OrderLine.unpriced(insulin, new OrderLineQuantity(2))))
                .confirmStock(List.of(new ReservedLinePrice(insulin, "Insulin 100IU",
                        new OrderLineUnitPrice(new BigDecimal("20.00")), fridge)))
                .approve("agent")
                .markDelivered(Instant.parse("2026-10-27T10:00:00Z"), DeliveryCheck.reported(true,
                        new ConditionReadings(new BigDecimal("3"), new BigDecimal("9.5"), null, null), "Warm box"))
                .releaseDelivery("qa.admin", "Within the stability budget", Instant.parse("2026-10-27T12:00:00Z"));
        repository.save(order);
        entityManager.flush();
        entityManager.clear();

        Order back = repository.findById(order.id()).orElseThrow();

        assertEquals(OrderStatus.DELIVERED, back.status());
        assertEquals(fridge, back.lines().get(0).storageCondition());
        assertEquals(order.deliveryCheck(), back.deliveryCheck());
        assertEquals(List.of("Insulin 100IU: Highest temperature 9.5 °C is above 8.0 °C."), back.deliveryCheck().breaches());
        assertEquals(order.deliveryDecision(), back.deliveryDecision());
    }
}
