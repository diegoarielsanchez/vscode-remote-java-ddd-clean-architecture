package com.das.catalog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Date;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.das.cleanddd.domain.catalog.reservation.ports.IStockReservationEventPublisher;
import com.das.infra.service.catalog.events.OrderEventListener;
import com.das.infra.service.catalog.outbox.OutboxStockReservationEventPublisher;
import com.das.cleanddd.domain.catalog.ports.IProductEventPublisher;
import com.das.cleanddd.domain.catalog.usecases.dtos.CreateProductInputDTO;
import com.das.infra.service.catalog.ProductJpaRepository;
import com.das.infra.service.catalog.outbox.ProductOutboxRelay;
import com.das.infra.service.catalog.outbox.OutboxEventEntity;
import com.das.infra.service.catalog.outbox.OutboxEventJpaRepository;
import com.das.infra.service.catalog.outbox.OutboxProductEventPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Jwts;

/**
 * Production wiring (non-dev profile): an HTTP create goes through the transactional use case
 * and leaves exactly one outbox row whose envelope names the authenticated user as actor.
 * The relay bean exists but its first run is delayed beyond the test, so no broker is needed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalogoutbox;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "eureka.client.enabled=false",
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false",
        "catalog.outbox.relay.initial-delay-ms=3600000",
        "catalog.events.listener.auto-startup=false",
        "jwt.secret=" + OutboxWiringTest.JWT_SECRET
})
class OutboxWiringTest {

    static final String JWT_SECRET = "test-secret-key-for-integration-tests-only-32chars";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private IProductEventPublisher publisher;
    @Autowired private ProductOutboxRelay relay;
    @Autowired private IStockReservationEventPublisher reservationPublisher;
    @Autowired private OrderEventListener orderEventListener;
    @Autowired private OutboxEventJpaRepository outbox;
    @Autowired private ProductJpaRepository rows;

    @AfterEach
    void cleanUp() {
        outbox.deleteAll();
        rows.deleteAll();
    }

    private static String jwtFor(String subject) {
        SecretKeySpec key = new SecretKeySpec(JWT_SECRET.getBytes(), "HmacSHA256");
        return Jwts.builder()
                .subject(subject)
                .claim("authorities", List.of("ROLE_USER"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(key)
                .compact();
    }

    @Test
    void usesTheOutboxPublisherAndRelayOutsideDev() {
        assertInstanceOf(OutboxProductEventPublisher.class, AopTestUtils.getUltimateTargetObject(publisher));
        // The stock saga: answers go through the same outbox, order events are consumed.
        assertInstanceOf(OutboxStockReservationEventPublisher.class, AopTestUtils.getUltimateTargetObject(reservationPublisher));
        assertNotNull(orderEventListener);
        assertNotNull(relay);
    }

    @Test
    void createWritesOneOutboxEventCarryingTheAuthenticatedActor() throws Exception {
        var body = new CreateProductInputDTO("Amoxicillin 500mg", "Antibiotic", new java.math.BigDecimal("12.50"), "BOX", 100);

        mockMvc.perform(post("/api/v1/products/create")
                        .header("Authorization", "Bearer " + jwtFor("rep.manager"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        List<OutboxEventEntity> events = outbox.findAll();
        assertEquals(1, events.size());
        JsonNode envelope = objectMapper.readTree(events.get(0).getPayload());
        assertEquals("catalog.product.created", envelope.get("eventType").asText());
        assertEquals("rep.manager", envelope.get("actor").asText());
        assertEquals(rows.findAll().get(0).getId(), envelope.get("aggregateId").asText());
    }

}
