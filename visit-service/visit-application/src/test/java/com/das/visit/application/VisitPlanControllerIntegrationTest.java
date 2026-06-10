package com.das.visit.application;

import com.das.cleanddd.domain.visit.ports.IHealthCareProfValidator;
import com.das.cleanddd.domain.visit.ports.IMedicalSalesRepValidator;
import com.das.cleanddd.domain.visit.ports.IProductPromoAttachmentStorage;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import com.das.cleanddd.domain.visit.usecases.dtos.CreateVisitPlanInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.UpdateVisitPlanInputDTO;
import com.das.infra.service.visit.VisitPlanJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.spec.SecretKeySpec;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full-stack integration tests for {@link VisitPlanController}.
 *
 * Boots the complete Spring context against an in-memory H2 database.
 * Uses a real JWT token (signed with the test secret) to satisfy
 * {@link com.das.visit.application.security.JwtAuthenticationFilter}.
 * RabbitMQ and Eureka are disabled via {@link TestPropertySource}.
 * External validators and attachment storage are replaced by Mockito mocks.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties")
class VisitPlanControllerIntegrationTest {

    private static final String JWT_SECRET = "test-secret-key-for-integration-tests-only-32chars";
    private static final String BASE_URL   = "/api/v1/visitplan";

    private static final String HCP_ID  = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String MSR_ID  = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
    private static final String SITE_ID = "cccccccc-cccc-cccc-cccc-cccccccccccc";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VisitPlanJpaRepository visitPlanJpaRepository;

    @MockBean
    private ConnectionFactory connectionFactory;

    @MockBean
    private IHealthCareProfValidator healthCareProfValidator;

    @MockBean
    private IMedicalSalesRepValidator medicalSalesRepValidator;

    @MockBean
    private IProductPromoAttachmentStorage attachmentStorage;

    private String authToken;

    @BeforeEach
    void setUp() {
        authToken = generateValidJwt();
        when(healthCareProfValidator.existsAndActive(any())).thenReturn(true);
        when(medicalSalesRepValidator.existsAndActive(any())).thenReturn(true);
    }

    @AfterEach
    void cleanUp() {
        visitPlanJpaRepository.deleteAll();
    }

    // ── JWT helper ──────────────────────────────────────────────────────────

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

    // ── CREATE ──────────────────────────────────────────────────────────────

    @Test
    void createVisitPlan_returns201_withVisitPlanBody() throws Exception {
        var body = new CreateVisitPlanInputDTO(
                LocalDateTime.now().plusDays(7), HCP_ID, "Scheduled review", SITE_ID, MSR_ID);

        mockMvc.perform(post(BASE_URL + "/create")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.healthCareProfId").value(HCP_ID))
                .andExpect(jsonPath("$.medicalSalesRepId").value(MSR_ID))
                .andExpect(jsonPath("$.visitSiteId").value(SITE_ID))
                .andExpect(jsonPath("$.visitComments").value("Scheduled review"));
    }

    @Test
    void createVisitPlan_returns403_whenNoToken() throws Exception {
        var body = new CreateVisitPlanInputDTO(
                LocalDateTime.now().plusDays(7), HCP_ID, "Comments", SITE_ID, MSR_ID);

        mockMvc.perform(post(BASE_URL + "/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createVisitPlan_returns400_whenDateIsInThePast() throws Exception {
        // @Future on visitDateTime should trigger a validation error
        var body = new CreateVisitPlanInputDTO(
                LocalDateTime.now().minusDays(1), HCP_ID, "Comments", SITE_ID, MSR_ID);

        mockMvc.perform(post(BASE_URL + "/create")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createVisitPlan_returns400_whenMsrIsNotActive() throws Exception {
        when(medicalSalesRepValidator.existsAndActive(MSR_ID)).thenReturn(false);

        var body = new CreateVisitPlanInputDTO(
                LocalDateTime.now().plusDays(7), HCP_ID, "Comments", SITE_ID, MSR_ID);

        mockMvc.perform(post(BASE_URL + "/create")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // ── UPDATE ──────────────────────────────────────────────────────────────

    @Test
    void updateVisitPlan_returns200_withUpdatedBody() throws Exception {
        String id = createAndGetId();

        var updateBody = new UpdateVisitPlanInputDTO(
                id, LocalDateTime.now().plusDays(14),
                HCP_ID, "Rescheduled appointment", SITE_ID, MSR_ID);

        mockMvc.perform(put(BASE_URL + "/update")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.visitComments").value("Rescheduled appointment"));
    }

    // ── GET ─────────────────────────────────────────────────────────────────

    @Test
    void getVisitPlanById_returns200_withBody() throws Exception {
        String id = createAndGetId();

        mockMvc.perform(get(BASE_URL + "/" + id)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.healthCareProfId").value(HCP_ID));
    }

    @Test
    void getVisitPlanById_returns400_whenNotFound() throws Exception {
        mockMvc.perform(get(BASE_URL + "/nonexistent-id")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isBadRequest());
    }

    // ── LIST ─────────────────────────────────────────────────────────────────

    @Test
    void listVisitPlans_returns200_withAllPlans() throws Exception {
        createAndGetId();
        createAndGetId();

        mockMvc.perform(post(BASE_URL + "/list")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    /** Offset in days so successive plans within one test get unique datetimes. */
    private int dayOffset = 7;

    private String createAndGetId() throws Exception {
        dayOffset += 1;
        var body = new CreateVisitPlanInputDTO(
                LocalDateTime.now().plusDays(dayOffset),
                HCP_ID, "Test plan", SITE_ID, MSR_ID);

        String response = mockMvc.perform(post(BASE_URL + "/create")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("id").asText();
    }
}
