package com.das.msr.application;

import com.das.cleanddd.domain.medicalsalesrep.usecases.dtos.CreateMedicalSalesRepInputDTO;
import com.das.cleanddd.domain.medicalsalesrep.usecases.dtos.MedicalSalesRepIDDto;
import com.das.cleanddd.domain.medicalsalesrep.usecases.dtos.UpdateMedicalSalesRepInputDTO;
import com.das.infra.service.medicalsalesrep.MedicalSalesRepJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.spec.SecretKeySpec;
import java.util.Date;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full-stack integration tests for {@link MedicalSalesRepController}.
 *
 * Boots the complete Spring context against an in-memory H2 database.
 * Uses a real JWT token (signed with the test secret) to satisfy the
 * {@link com.das.msr.application.security.JwtAuthenticationFilter}.
 * RabbitMQ and Eureka are disabled; the dev-profile no-op event publisher
 * is active so no broker is needed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        // Replace PostgreSQL with H2
        "spring.datasource.url=jdbc:h2:mem:msrtest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        // Disable RabbitMQ auto-configuration (no broker needed in tests)
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
        // Disable Eureka (no service registry needed in tests)
        "eureka.client.enabled=false",
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false",
        // JWT secret satisfying the 32-char minimum enforced by JwtSecretValidator
        "jwt.secret=test-secret-key-for-integration-tests-only-32chars"
})
class MedicalSalesRepControllerTest {

    private static final String JWT_SECRET = "test-secret-key-for-integration-tests-only-32chars";
    private static final String BASE_URL = "/api/v1/medicalsalesrep";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MedicalSalesRepJpaRepository jpaRepository;

    private String authToken;

    @BeforeEach
    void setUp() {
        authToken = generateValidJwt();
    }

    @AfterEach
    void cleanUp() {
        jpaRepository.deleteAll();
    }

    // ── JWT helper ───────────────────────────────────────────────────────────

    private String generateValidJwt() {
        SecretKeySpec key = new SecretKeySpec(
                JWT_SECRET.getBytes(), 0, JWT_SECRET.getBytes().length, "HmacSHA256");
        return Jwts.builder()
                .subject("testuser")
                .claim("authorities", List.of("ROLE_USER"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(key)
                .compact();
    }

    // ── CREATE ───────────────────────────────────────────────────────────────

    @Test
    void create_returns201_withCreatedRepBody() throws Exception {
        var body = new CreateMedicalSalesRepInputDTO("Alice", "Smith", "alice@example.com");

        final MediaType application_JSON2 = MediaType.APPLICATION_JSON;
        if (application_JSON2 != null) {
                mockMvc.perform(post(BASE_URL + "/create")
                                .header("Authorization", "Bearer " + authToken)
                                .contentType(application_JSON2)
                                .content(objectMapper.writeValueAsString(body)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.name").value("Alice"))
                        .andExpect(jsonPath("$.surname").value("Smith"))
                        .andExpect(jsonPath("$.email").value("alice@example.com"))
                        .andExpect(jsonPath("$.active").value(false))
                        .andExpect(jsonPath("$.id").isNotEmpty());
        } else {
                // TODO handle null value
        }
    }

    @Test
    void create_returns400_whenEmailAlreadyExists() throws Exception {
        var body = new CreateMedicalSalesRepInputDTO("Alice", "Smith", "alice@example.com");

        mockMvc.perform(post(BASE_URL + "/create")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        // Second request with the same email must be rejected
        mockMvc.perform(post(BASE_URL + "/create")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_returns403_whenAuthTokenIsAbsent() throws Exception {
        var body = new CreateMedicalSalesRepInputDTO("Alice", "Smith", "alice@example.com");

        mockMvc.perform(post(BASE_URL + "/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    // ── UPDATE ───────────────────────────────────────────────────────────────

    @Test
    void update_returns200_withUpdatedRepBody() throws Exception {
        String id = createAndGetId("Bob", "Jones", "bob@example.com");

        var updateBody = new UpdateMedicalSalesRepInputDTO(id, "Robert", "Jones", "robert@example.com");

        mockMvc.perform(put(BASE_URL + "/update")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Robert"))
                .andExpect(jsonPath("$.email").value("robert@example.com"));
    }

    // ── ACTIVATE ─────────────────────────────────────────────────────────────

    @Test
    void activate_returns200_andRepBecomesActive() throws Exception {
        String id = createAndGetId("Carol", "White", "carol@example.com");

        mockMvc.perform(post(BASE_URL + "/activate")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MedicalSalesRepIDDto(id))))
                .andExpect(status().isOk());

        // Verify the active flag was flipped
        mockMvc.perform(get(BASE_URL + "/get")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MedicalSalesRepIDDto(id))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    // ── DEACTIVATE ───────────────────────────────────────────────────────────

    @Test
    void deactivate_returns200_andRepBecomesInactive() throws Exception {
        String id = createAndGetId("Dave", "Brown", "dave@example.com");

        // Activate first (MSRs are created inactive)
        mockMvc.perform(post(BASE_URL + "/activate")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MedicalSalesRepIDDto(id))))
                .andExpect(status().isOk());

        // Then deactivate
        mockMvc.perform(post(BASE_URL + "/deactivate")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MedicalSalesRepIDDto(id))))
                .andExpect(status().isOk());

        // Verify the active flag was cleared
        mockMvc.perform(get(BASE_URL + "/get")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MedicalSalesRepIDDto(id))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    // ── GET ───────────────────────────────────────────────────────────────────

    @Test
    void get_returns200_withCorrectRepData() throws Exception {
        String id = createAndGetId("Eve", "Green", "eve@example.com");

        mockMvc.perform(get(BASE_URL + "/get")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MedicalSalesRepIDDto(id))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Eve"))
                .andExpect(jsonPath("$.surname").value("Green"))
                .andExpect(jsonPath("$.email").value("eve@example.com"));
    }

    @Test
    void get_returns400_whenRepNotFound() throws Exception {
        var body = new MedicalSalesRepIDDto(java.util.UUID.randomUUID().toString());

        mockMvc.perform(get(BASE_URL + "/get")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // ── LIST ─────────────────────────────────────────────────────────────────

    @Test
    void list_returns200_withFilteredResults() throws Exception {
        createAndGetId("Frank", "Miller", "frank@example.com");
        createAndGetId("Grace", "Taylor", "grace@example.com");

        // Search for "Frank Miller" specifically — only one record should match
        mockMvc.perform(post(BASE_URL + "/list")
                        .header("Authorization", "Bearer " + authToken)
                        .param("firstName", "Frank")
                        .param("lastName", "Miller")
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Frank"))
                .andExpect(jsonPath("$[0].surname").value("Miller"));
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    /**
     * Creates a medical sales rep via the API and returns the assigned ID.
     */
    private String createAndGetId(String firstName, String lastName, String email) throws Exception {
        var body = new CreateMedicalSalesRepInputDTO(firstName, lastName, email);
        String response = mockMvc.perform(post(BASE_URL + "/create")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }
}
