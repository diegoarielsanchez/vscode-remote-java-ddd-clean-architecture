package com.das.order.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

import com.das.cleanddd.domain.order.entities.IOrderRepository;
import com.das.cleanddd.domain.order.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.order.entities.Order;
import com.das.cleanddd.domain.order.entities.OrderLine;
import com.das.cleanddd.domain.order.entities.OrderLineQuantity;
import com.das.cleanddd.domain.order.entities.OrderLineUnitPrice;
import com.das.cleanddd.domain.order.entities.ProductId;
import com.das.cleanddd.domain.order.entities.ReservedLinePrice;
import com.das.cleanddd.domain.order.ports.IMedicalSalesRepValidator;
import com.das.cleanddd.domain.shared.storage.StorageCondition;
import com.das.cleanddd.domain.order.ports.IOrderEventPublisher;
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
 * Production wiring (non-dev profile): an HTTP create is accepted (202, AWAITING_STOCK) and leaves
 * exactly one outbox row, {@code order.created} with the lines to reserve, whose envelope names the
 * authenticated user as actor. The relay's first run is delayed beyond the test and the catalog
 * listener does not start, so no broker is needed.
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
        "order.events.listener.auto-startup=false",
        "jwt.secret=" + OutboxWiringTest.JWT_SECRET
})
class OutboxWiringTest {

    static final String JWT_SECRET = "test-secret-key-for-integration-tests-only-32chars";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private IOrderEventPublisher publisher;
    @MockitoBean private IMedicalSalesRepValidator medicalSalesRepValidator; // remote: msr-service
    @Autowired private OrderOutboxRelay relay;
    @Autowired private OutboxEventJpaRepository outbox;
    @Autowired private OrderJpaRepository rows;
    @Autowired private IOrderRepository orders;

    @AfterEach
    void cleanUp() {
        outbox.deleteAll();
        rows.deleteAll();
    }

    private static String jwtFor(String subject, String msrId) {
        SecretKeySpec key = new SecretKeySpec(JWT_SECRET.getBytes(), "HmacSHA256");
        return Jwts.builder()
                .subject(subject)
                .claim("authorities", List.of("ROLE_USER"))
                .claim("msr_id", msrId) // orders are placed for the caller's own MSR
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
    void createIsAcceptedAndWritesOnlyOrderCreatedCarryingTheAuthenticatedActor() throws Exception {
        String msr = java.util.UUID.randomUUID().toString();
        String product = java.util.UUID.randomUUID().toString();
        when(medicalSalesRepValidator.existsAndActive(msr)).thenReturn(true);
        var body = new CreateOrderInputDTO(msr, List.of(new OrderLineInputDTO(product, 2)));

        mockMvc.perform(post("/api/v1/orders/create")
                        .header("Authorization", "Bearer " + jwtFor("rep.manager", msr))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("AWAITING_STOCK"))
                .andExpect(header().string("Location", "/api/v1/orders/" + rows.findAll().get(0).getId()));

        List<OutboxEventEntity> events = outbox.findAll().stream()
                .sorted(java.util.Comparator.comparingLong(OutboxEventEntity::getAggregateVersion)).toList();
        assertEquals(List.of("order.created"),
                events.stream().map(OutboxEventEntity::getEventType).toList());
        JsonNode created = objectMapper.readTree(events.get(0).getPayload());
        assertEquals("rep.manager", created.get("actor").asText());
        assertEquals(msr, created.at("/data/medicalSalesRepId").asText());
        assertEquals(product, created.at("/data/lines/0/productId").asText());
        assertEquals(2, created.at("/data/lines/0/quantity").asInt());
        assertEquals(rows.findAll().get(0).getId(), created.get("aggregateId").asText());
    }

    private static String adminJwt() {
        SecretKeySpec key = new SecretKeySpec(JWT_SECRET.getBytes(), "HmacSHA256");
        return Jwts.builder()
                .subject("qa.admin")
                .claim("authorities", List.of("ROLE_USER", "ROLE_ADMIN"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(key)
                .compact();
    }

    @Test
    void aWarmColdChainDeliveryIsQuarantinedUntilQualityReleasesIt() throws Exception {
        String msr = java.util.UUID.randomUUID().toString();
        ProductId insulin = new ProductId(java.util.UUID.randomUUID().toString());
        Order approved = Order.create(new MedicalSalesRepId(msr), List.of(OrderLine.unpriced(insulin, new OrderLineQuantity(2))))
                .confirmStock(List.of(new ReservedLinePrice(insulin, "Insulin 100IU",
                        new OrderLineUnitPrice(new java.math.BigDecimal("20.00")),
                        new StorageCondition(new java.math.BigDecimal("2"), new java.math.BigDecimal("8"), null, false))))
                .approve("agent");
        orders.save(approved);
        String id = approved.id().value();

        mockMvc.perform(post("/api/v1/orders/" + id + "/confirm-delivery")
                        .header("Authorization", "Bearer " + jwtFor("rep", msr)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/orders/" + id + "/confirm-delivery")
                        .header("Authorization", "Bearer " + jwtFor("rep", msr))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conditionsMaintained\":true,\"minTemperatureC\":3.0,\"maxTemperatureC\":11.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERY_QUARANTINED"))
                .andExpect(jsonPath("$.transportCheckRequired").value(true))
                .andExpect(jsonPath("$.lines[0].storageCondition.coldChain").value(true))
                .andExpect(jsonPath("$.deliveryCheck.breaches[0]")
                        .value("Insulin 100IU: Highest temperature 11.0 °C is above 8.0 °C."));

        mockMvc.perform(post("/api/v1/orders/" + id + "/delivery/release")
                        .header("Authorization", "Bearer " + adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Within the stability budget for 2 h at 11 °C\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.deliveryCheck.decidedBy").value("qa.admin"));

        List<String> types = outbox.findAll().stream()
                .sorted(java.util.Comparator.comparingLong(OutboxEventEntity::getAggregateVersion))
                .map(OutboxEventEntity::getEventType).toList();
        assertEquals(List.of("order.delivery-quarantined", "order.delivery-released", "order.delivered"),
                types.subList(types.size() - 3, types.size()));
    }
}
