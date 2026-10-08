package com.das.order.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.das.cleanddd.domain.order.ports.IMedicalSalesRepValidator;
import com.das.cleanddd.domain.order.ports.IOrderEventPublisher;
import com.das.cleanddd.domain.order.ports.IProductStockPort;
import com.das.cleanddd.domain.order.ports.IProductStockPort.StockReservationResult;
import com.das.cleanddd.domain.order.usecases.dtos.CreateOrderInputDTO;
import com.das.cleanddd.domain.order.usecases.dtos.OrderLineInputDTO;
import com.das.infra.service.order.OrderJpaRepository;
import com.das.infra.service.order.outbox.OrderOutboxRelay;
import com.das.infra.service.order.outbox.OutboxEventEntity;
import com.das.infra.service.order.outbox.OutboxEventJpaRepository;
import com.das.infra.service.order.outbox.OutboxOrderEventPublisher;
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
        "spring.datasource.url=jdbc:h2:mem:orderoutbox;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "eureka.client.enabled=false",
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false",
        "order.outbox.relay.initial-delay-ms=3600000",
        "jwt.secret=" + OutboxWiringTest.JWT_SECRET
})
class OutboxWiringTest {

    static final String JWT_SECRET = "test-secret-key-for-integration-tests-only-32chars";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private IOrderEventPublisher publisher;
    @MockitoBean private IMedicalSalesRepValidator medicalSalesRepValidator; // remote: msr-service
    @MockitoBean private IProductStockPort productStockPort;                 // remote: product-catalog-service
    @Autowired private OrderOutboxRelay relay;
    @Autowired private OutboxEventJpaRepository outbox;
    @Autowired private OrderJpaRepository rows;

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
        assertInstanceOf(OutboxOrderEventPublisher.class, AopTestUtils.getUltimateTargetObject(publisher));
        assertNotNull(relay);
    }

    @Test
    void createWritesBothLifecycleEventsCarryingTheAuthenticatedActor() throws Exception {
        String msr = java.util.UUID.randomUUID().toString();
        String product = java.util.UUID.randomUUID().toString();
        when(medicalSalesRepValidator.existsAndActive(msr)).thenReturn(true);
        when(productStockPort.reserve(anyString(), anyInt()))
                .thenReturn(new StockReservationResult(true, 8, new java.math.BigDecimal("12.50"), "Amoxicillin 500mg"));
        var body = new CreateOrderInputDTO(msr, List.of(new OrderLineInputDTO(product, 2)));

        mockMvc.perform(post("/api/v1/orders/create")
                        .header("Authorization", "Bearer " + jwtFor("rep.manager"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        List<OutboxEventEntity> events = outbox.findAll().stream()
                .sorted(java.util.Comparator.comparingLong(OutboxEventEntity::getAggregateVersion)).toList();
        assertEquals(List.of("order.created", "order.submitted-for-approval"),
                events.stream().map(OutboxEventEntity::getEventType).toList());
        JsonNode created = objectMapper.readTree(events.get(0).getPayload());
        assertEquals("rep.manager", created.get("actor").asText());
        assertEquals(msr, created.at("/data/medicalSalesRepId").asText());
        assertEquals(rows.findAll().get(0).getId(), created.get("aggregateId").asText());
    }

}
