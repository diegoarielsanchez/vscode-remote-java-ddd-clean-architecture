package com.das.hcp.application;

import com.das.cleanddd.domain.healthcareprof.usecases.dtos.CreateHealthCareProfInputDTO;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.HealthCareProfIDDto;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.UpdateHealthCareProfInputDTO;
import com.das.infra.service.healthcareprof.HealthCareProfJpaRepository;
import tools.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.spec.SecretKeySpec;
import java.util.Date;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:hcptest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.autoconfigure.exclude=org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration",
        "eureka.client.enabled=false",
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false",
        "jwt.secret=test-secret-key-for-integration-tests-only-32chars"
})
class HealthCareProfControllerTest {

    private static final String JWT_SECRET = "test-secret-key-for-integration-tests-only-32chars";
    private static final String BASE_URL = "/api/v1/healthcareprof";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HealthCareProfJpaRepository jpaRepository;

    private String authToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        authToken = generateValidJwt("ROLE_USER");
        adminToken = generateValidJwt("ROLE_USER", "ROLE_ADMIN");
    }

    @AfterEach
    void cleanUp() {
        jpaRepository.deleteAll();
    }

    private String generateValidJwt(String... authorities) {
        SecretKeySpec key = new SecretKeySpec(
                JWT_SECRET.getBytes(), 0, JWT_SECRET.getBytes().length, "HmacSHA256");
        return Jwts.builder()
                .subject("testuser")
                .claim("authorities", List.of(authorities))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(key)
                .compact();
    }

    @Test
    void create_returns201_withCreatedProfBody() throws Exception {
        var body = new CreateHealthCareProfInputDTO("Alice", "Smith", "alice@example.com", List.of("CARD", "DERM"));

        mockMvc.perform(post(BASE_URL + "/create")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.surname").value("Smith"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.specialties", hasSize(2)))
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    void create_returns400_whenEmailAlreadyExists() throws Exception {
        var body = new CreateHealthCareProfInputDTO("Alice", "Smith", "alice@example.com", List.of("CARD"));

        mockMvc.perform(post(BASE_URL + "/create")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(BASE_URL + "/create")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_returns403_whenAuthTokenIsAbsent() throws Exception {
        var body = new CreateHealthCareProfInputDTO("Alice", "Smith", "alice@example.com", List.of("CARD"));

        mockMvc.perform(post(BASE_URL + "/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_returns200_withUpdatedProfBody() throws Exception {
        String id = createAndGetId("Bob", "Jones", "bob@example.com", List.of("CARD"));

        var updateBody = new UpdateHealthCareProfInputDTO(id, "Robert", "Jones", "robert@example.com", List.of("DERM", "NEUR"),
                "Name corrected at the HCP's request");

        mockMvc.perform(put(BASE_URL + "/update")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Robert"))
                .andExpect(jsonPath("$.email").value("robert@example.com"))
                .andExpect(jsonPath("$.specialties", hasSize(2)));
    }

    @Test
    void activate_returns200_andProfBecomesActive() throws Exception {
        String id = createAndGetId("Carol", "White", "carol@example.com", List.of("CARD"));

        mockMvc.perform(post(BASE_URL + "/" + id + "/activate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new HealthCareProfIDDto(id))))
                .andExpect(status().isOk());

        mockMvc.perform(get(BASE_URL + "/" + id)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void deactivate_returns200_andProfBecomesInactive() throws Exception {
        String id = createAndGetId("Dave", "Brown", "dave@example.com", List.of("CARD"));

        mockMvc.perform(post(BASE_URL + "/" + id + "/activate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new HealthCareProfIDDto(id))))
                .andExpect(status().isOk());

        mockMvc.perform(post(BASE_URL + "/" + id + "/deactivate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new HealthCareProfIDDto(id))))
                .andExpect(status().isOk());

        mockMvc.perform(get(BASE_URL + "/" + id)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void get_returns200_withCorrectProfData() throws Exception {
        String id = createAndGetId("Eve", "Green", "eve@example.com", List.of("CARD", "DERM"));

        mockMvc.perform(get(BASE_URL + "/" + id)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Eve"))
                .andExpect(jsonPath("$.surname").value("Green"))
                .andExpect(jsonPath("$.email").value("eve@example.com"))
                .andExpect(jsonPath("$.specialties", hasSize(2)));
    }

    @Test
    void get_returns400_whenProfNotFound() throws Exception {
        String nonExistentId = java.util.UUID.randomUUID().toString();

        mockMvc.perform(get(BASE_URL + "/" + nonExistentId)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_returns200_withFilteredResults() throws Exception {
        createAndGetId("Frank", "Miller", "frank@example.com", List.of("CARD"));
        createAndGetId("Grace", "Taylor", "grace@example.com", List.of("DERM"));

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

    @Test
    void specialties_returns200_withPredefinedCatalog() throws Exception {
        mockMvc.perform(get(BASE_URL + "/specialties")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(23)))
                .andExpect(jsonPath("$[0].code").value("CARD"));
    }

    private String createAndGetId(String firstName, String lastName, String email, List<String> specialties) throws Exception {
        var body = new CreateHealthCareProfInputDTO(firstName, lastName, email, specialties);
        String response = mockMvc.perform(post(BASE_URL + "/create")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asString();
    }

    @Test
    void activateAndDeactivate_areAdminOnly_regularUserGets403() throws Exception {
        // OWASP A01: the security chain refuses before the handler runs, so any id will do.
        String id = java.util.UUID.randomUUID().toString();
        for (String action : List.of("activate", "deactivate")) {
            mockMvc.perform(post(BASE_URL + "/" + id + "/" + action)
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    // ── AUDIT TRAIL ─────────────────────────────────────────────────────────

    @Test
    void history_isForAdminsOnly() throws Exception {
        String id = java.util.UUID.randomUUID().toString();

        mockMvc.perform(get(BASE_URL + "/" + id + "/history")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isForbidden());

        // The dev profile writes no events, so the trail is empty here; the infra tests cover its content.
        mockMvc.perform(get(BASE_URL + "/" + id + "/history")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
