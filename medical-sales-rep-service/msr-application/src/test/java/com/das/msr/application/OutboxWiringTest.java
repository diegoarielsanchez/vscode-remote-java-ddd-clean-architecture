package com.das.msr.application;

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

import com.das.cleanddd.domain.medicalsalesrep.ports.IMsrEventPublisher;
import com.das.cleanddd.domain.medicalsalesrep.usecases.dtos.CreateMedicalSalesRepInputDTO;
import com.das.infra.service.medicalsalesrep.MedicalSalesRepJpaRepository;
import com.das.infra.service.medicalsalesrep.outbox.MsrOutboxRelay;
import com.das.infra.service.medicalsalesrep.outbox.OutboxEventEntity;
import com.das.infra.service.medicalsalesrep.outbox.OutboxEventJpaRepository;
import com.das.infra.service.medicalsalesrep.outbox.OutboxMsrEventPublisher;
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
        "spring.datasource.url=jdbc:h2:mem:msroutbox;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "eureka.client.enabled=false",
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false",
        "msr.outbox.relay.initial-delay-ms=3600000",
        "jwt.secret=" + OutboxWiringTest.JWT_SECRET
})
class OutboxWiringTest {

    static final String JWT_SECRET = "test-secret-key-for-integration-tests-only-32chars";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private IMsrEventPublisher publisher;
    @Autowired private MsrOutboxRelay relay;
    @Autowired private OutboxEventJpaRepository outbox;
    @Autowired private MedicalSalesRepJpaRepository msrRows;

    @AfterEach
    void cleanUp() {
        outbox.deleteAll();
        msrRows.deleteAll();
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
        assertInstanceOf(OutboxMsrEventPublisher.class, AopTestUtils.getUltimateTargetObject(publisher));
        assertNotNull(relay);
    }

    @Test
    void createWritesOneOutboxEventCarryingTheAuthenticatedActor() throws Exception {
        var body = new CreateMedicalSalesRepInputDTO("Alice", "Smith", "alice@example.com");

        mockMvc.perform(post("/api/v1/medicalsalesrep/create")
                        .header("Authorization", "Bearer " + jwtFor("rep.manager"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        List<OutboxEventEntity> rows = outbox.findAll();
        assertEquals(1, rows.size());
        JsonNode envelope = objectMapper.readTree(rows.get(0).getPayload());
        assertEquals("msr.created", envelope.get("eventType").asText());
        assertEquals("rep.manager", envelope.get("actor").asText());
        assertEquals(msrRows.findAll().get(0).getId(), envelope.get("aggregateId").asText());
    }

}
