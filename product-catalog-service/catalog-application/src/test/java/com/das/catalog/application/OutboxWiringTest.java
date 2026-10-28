package com.das.catalog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.MockMvc;

import com.das.cleanddd.domain.catalog.reservation.ports.IStockReservationEventPublisher;
import com.das.infra.service.catalog.events.OrderEventListener;
import com.das.infra.service.catalog.outbox.OutboxStockReservationEventPublisher;
import com.das.cleanddd.domain.catalog.ports.IProductEventPublisher;
import com.das.cleanddd.domain.catalog.usecases.dtos.CreateProductInputDTO;
import com.das.infra.service.catalog.ProductJpaRepository;
import com.das.infra.service.catalog.lot.StockLotJpaRepository;
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
    @Autowired private StockLotJpaRepository lotRows;

    @AfterEach
    void cleanUp() {
        outbox.deleteAll();
        lotRows.deleteAll();
        rows.deleteAll();
    }

    private static String jwtFor(String subject) {
        SecretKeySpec key = new SecretKeySpec(JWT_SECRET.getBytes(), "HmacSHA256");
        return Jwts.builder()
                .subject(subject)
                .claim("authorities", List.of("ROLE_USER", "ROLE_ADMIN")) // creating products is admin-only
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
        var body = new CreateProductInputDTO("Amoxicillin 500mg", "Antibiotic", new java.math.BigDecimal("12.50"), "BOX", 0,
                null, null, null, null, new com.das.cleanddd.domain.catalog.usecases.dtos.StorageConditionDTO(
                        new java.math.BigDecimal("15"), new java.math.BigDecimal("25"), null, false));

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

    private static String jwtWith(String subject, String... authorities) {
        SecretKeySpec key = new SecretKeySpec(JWT_SECRET.getBytes(), "HmacSHA256");
        return Jwts.builder()
                .subject(subject)
                .claim("authorities", List.of(authorities))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(key)
                .compact();
    }

    @Test
    void aProductWithAGtinReceivesLotsByGs1CodeAndAQuarantineIsRecordedWithItsActor() throws Exception {
        String admin = "Bearer " + jwtFor("qa.admin");
        String agent = "Bearer " + jwtWith("ai.agent", "ROLE_USER", "ROLE_AGENT");
        String created = mockMvc.perform(post("/api/v1/products/create")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Insulin 100IU","price":20.00,"unit":"VIAL","initialStock":5,
                                 "gtin":"9506000134352","batchNumber":"L2026-07A","expirationDate":"2075-03-31",
                                 "storageCondition":{"minTemperatureC":2,"maxTemperatureC":8,"protectFromLight":true}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gtin").value("09506000134352"))
                .andExpect(jsonPath("$.stock").value(5))
                .andExpect(jsonPath("$.storageCondition.coldChain").value(true))
                .andExpect(jsonPath("$.storageCondition.description").value("2.0 to 8.0 °C, protect from light"))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(created).get("id").asText();

        mockMvc.perform(post("/api/v1/products/" + id + "/restock")
                        .header("Authorization", agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":10,\"gs1\":\"(01)09506000134352(17)750331(10)L2026-07A(21)SN1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchNumber").value("L2026-07A"))
                .andExpect(jsonPath("$.quantity").value(15));

        mockMvc.perform(post("/api/v1/products/" + id + "/lots/quarantine")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchNumber\":\"L2026-07A\",\"reason\":\"Recall R-2026-17\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUARANTINED"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/products/" + id + "/lots")
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].allocatable").value(false));

        JsonNode quarantined = outbox.findAll().stream()
                .filter(e -> e.getEventType().equals("catalog.product.lot-quarantined"))
                .map(e -> { try { return objectMapper.readTree(e.getPayload()); } catch (Exception x) { throw new IllegalStateException(x); } })
                .findFirst().orElseThrow();
        assertEquals("qa.admin", quarantined.get("actor").asText());
        assertEquals("Recall R-2026-17", quarantined.at("/data/reason").asText());
    }

    @Test
    void createRefusesStockWithoutALotAndAWrongGtinCheckDigit() throws Exception {
        for (String extra : List.of("\"initialStock\":10", "\"gtin\":\"09506000134353\"",
                "\"lotTracked\":true,\"storageCondition\":{\"minTemperatureC\":8,\"maxTemperatureC\":2}")) {
            mockMvc.perform(post("/api/v1/products/create")
                            .header("Authorization", "Bearer " + jwtFor("qa.admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Insulin\",\"price\":20.00,\"unit\":\"VIAL\","
                                    + "\"storageCondition\":{\"minTemperatureC\":2,\"maxTemperatureC\":8}," + extra + "}"))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(0, rows.count(), "nothing stored");
        assertEquals(0, outbox.count(), "no event recorded");
    }

    @Test
    void aBreachOfTheStorageConditionsQuarantinesTheLotAndQualityRejectsIt() throws Exception {
        String admin = "Bearer " + jwtFor("qa.admin");
        String agent = "Bearer " + jwtWith("monitoring", "ROLE_USER", "ROLE_AGENT");
        String created = mockMvc.perform(post("/api/v1/products/create")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Insulin 100IU","price":20.00,"unit":"VIAL","initialStock":5,
                                 "batchNumber":"L2026-07A","expirationDate":"2075-03-31",
                                 "storageCondition":{"minTemperatureC":2,"maxTemperatureC":8}}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(created).get("id").asText();

        mockMvc.perform(post("/api/v1/products/" + id + "/lots/excursions")
                        .header("Authorization", agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"batchNumber":"L2026-07A","source":"Fridge 3 logger",
                                 "observedFrom":"2026-10-23T22:00:00Z","observedTo":"2026-10-24T06:00:00Z",
                                 "minTemperatureC":3.1,"maxTemperatureC":12.4}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.withinConditions").value(false))
                .andExpect(jsonPath("$.quarantined").value(true))
                .andExpect(jsonPath("$.breaches[0]").value("Highest temperature 12.4 °C is above 8.0 °C."))
                .andExpect(jsonPath("$.lot.status").value("QUARANTINED"));

        mockMvc.perform(post("/api/v1/products/" + id + "/lots/reject")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchNumber\":\"L2026-07A\",\"reason\":\"Stability data do not cover 12.4 °C\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/products/" + id)
                        .header("Authorization", admin))
                .andExpect(jsonPath("$.stock").value(0));
        List<String> types = outbox.findAll().stream().map(OutboxEventEntity::getEventType).toList();
        assertTrue(types.containsAll(List.of("catalog.product.lot-excursion-recorded", "catalog.product.lot-quarantined",
                "catalog.product.lot-rejected")), types.toString());
    }
}
