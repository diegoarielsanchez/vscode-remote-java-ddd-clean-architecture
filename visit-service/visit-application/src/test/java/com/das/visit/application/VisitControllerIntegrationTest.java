package com.das.visit.application;

import com.das.cleanddd.domain.visit.ports.IHealthCareProfValidator;
import com.das.cleanddd.domain.visit.ports.IMedicalSalesRepValidator;
import com.das.cleanddd.domain.visit.ports.IProductPromoAttachmentStorage;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import com.das.cleanddd.domain.visit.usecases.dtos.CreateVisitInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.UpdateVisitInputDTO;
import com.das.infra.service.visit.VisitJpaRepository;
import com.das.cleanddd.domain.shared.LargeFileValueObject;
import tools.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.spec.SecretKeySpec;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full-stack integration tests for {@link VisitController}.
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
class VisitControllerIntegrationTest {

    private static final String JWT_SECRET = "test-secret-key-for-integration-tests-only-32chars";
    private static final String BASE_URL = "/api/v1/visit";

    private static final String HCP_ID  = "11111111-1111-1111-1111-111111111111";
    private static final String MSR_ID  = "22222222-2222-2222-2222-222222222222";
    private static final String SITE_ID = "33333333-3333-3333-3333-333333333333";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VisitJpaRepository visitJpaRepository;

        @MockitoBean
    private ConnectionFactory connectionFactory;

        @MockitoBean
    private IHealthCareProfValidator healthCareProfValidator;

        @MockitoBean
    private IMedicalSalesRepValidator medicalSalesRepValidator;

        @MockitoBean
    private IProductPromoAttachmentStorage attachmentStorage;

    private String authToken;

    @BeforeEach
    void setUp() {
        authToken = generateValidJwt();
        when(healthCareProfValidator.existsAndActive(any())).thenReturn(true);
        when(medicalSalesRepValidator.existsAndActive(any())).thenReturn(true);
        when(attachmentStorage.store(anyString(), anyString(), anyString(), anyLong(), any()))
                .thenReturn(LargeFileValueObject.ofMetadata("promo.png", "image/png", 1024L,
                        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"));
    }

    @AfterEach
    void cleanUp() {
        visitJpaRepository.deleteAll();
    }

    // ── Ownership: a visit always belongs to the caller's own MSR (OWASP A01) ──

    private static final String OTHER_MSR_ID = "44444444-4444-4444-4444-444444444444";

    @Test
    void createVisit_returns403_whenCallerIsNotLinkedToAnMsr() throws Exception {
        var body = new CreateVisitInputDTO(LocalDate.now().minusDays(1), HCP_ID, null, SITE_ID, null);

        mockMvc.perform(post(BASE_URL + "/create")
                        .header("Authorization", "Bearer " + jwtFor(null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createVisit_returns403_whenBodyNamesAnotherMsr() throws Exception {
        var body = new CreateVisitInputDTO(LocalDate.now().minusDays(1), HCP_ID, null, SITE_ID, OTHER_MSR_ID);

        mockMvc.perform(post(BASE_URL + "/create")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createVisit_usesCallersMsr_whenBodyOmitsIt() throws Exception {
        var body = new CreateVisitInputDTO(LocalDate.now().minusDays(1), HCP_ID, null, SITE_ID, null);

        mockMvc.perform(post(BASE_URL + "/create")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.medicalSalesRepId").value(MSR_ID));
    }

    @Test
    void updateVisit_cannotTouchAnotherMsrsVisit() throws Exception {
        String id = createAndGetId();
        var body = new UpdateVisitInputDTO(id, LocalDate.now().minusDays(1), HCP_ID, "hijack", SITE_ID, null);

        int status = mockMvc.perform(put(BASE_URL + "/update")
                        .header("Authorization", "Bearer " + jwtFor(OTHER_MSR_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn().getResponse().getStatus();

        org.junit.jupiter.api.Assertions.assertTrue(status >= 400 && status < 500, "expected a 4xx, got " + status);
        mockMvc.perform(get(BASE_URL + "/" + id).header("Authorization", "Bearer " + authToken))
                .andExpect(jsonPath("$.medicalSalesRepId").value(MSR_ID))
                .andExpect(jsonPath("$.visitComments").value(org.hamcrest.Matchers.not("hijack")));
    }

    // ── JWT helper ──────────────────────────────────────────────────────────

    private String generateValidJwt() {
        return jwtFor(MSR_ID);
    }

    /** A ROLE_USER token for the given MSR, or with no msr_id claim when {@code msrId} is null. */
    private String jwtFor(String msrId) {
        SecretKeySpec key = new SecretKeySpec(
                JWT_SECRET.getBytes(), 0, JWT_SECRET.getBytes().length, "HmacSHA256");
        return Jwts.builder()
                .subject("testuser")
                .claim("authorities", List.of("ROLE_USER"))
                .claim("msr_id", msrId) // visits always belong to the caller's own MSR
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(key)
                .compact();
    }

    // ── CREATE ──────────────────────────────────────────────────────────────

    @Test
    void createVisit_storesTheProductsHandedOut() throws Exception {
        String product = "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee1";
        var body = new com.das.cleanddd.domain.visit.usecases.dtos.CreateVisitInputDTO(
                LocalDate.now().minusDays(2), HCP_ID, "Samples", SITE_ID, MSR_ID,
                List.of(new com.das.cleanddd.domain.visit.usecases.dtos.VisitItemDTO(product, 6)));

        String response = mockMvc.perform(post(BASE_URL + "/create")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(response).get("id").asString();

        mockMvc.perform(get(BASE_URL + "/" + id).header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(product))
                .andExpect(jsonPath("$.items[0].quantity").value(6));
    }

    @Test
    void createVisit_returns201_withVisitBody() throws Exception {
        var body = new CreateVisitInputDTO(
                LocalDate.now().minusDays(1), HCP_ID, "Routine check", SITE_ID, MSR_ID);

        mockMvc.perform(post(BASE_URL + "/create")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.healthCareProfId").value(HCP_ID))
                .andExpect(jsonPath("$.medicalSalesRepId").value(MSR_ID))
                .andExpect(jsonPath("$.visitSiteId").value(SITE_ID))
                .andExpect(jsonPath("$.visitComments").value("Routine check"));
    }

    @Test
    void createVisit_returns403_whenNoToken() throws Exception {
        var body = new CreateVisitInputDTO(
                LocalDate.now().minusDays(1), HCP_ID, "Comments", SITE_ID, MSR_ID);

        mockMvc.perform(post(BASE_URL + "/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createVisit_returns400_whenMsrIsNotActive() throws Exception {
        when(medicalSalesRepValidator.existsAndActive(MSR_ID)).thenReturn(false);

        var body = new CreateVisitInputDTO(
                LocalDate.now().minusDays(1), HCP_ID, "Comments", SITE_ID, MSR_ID);

        mockMvc.perform(post(BASE_URL + "/create")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createVisit_returns400_whenHcpIsNotActive() throws Exception {
        when(healthCareProfValidator.existsAndActive(HCP_ID)).thenReturn(false);

        var body = new CreateVisitInputDTO(
                LocalDate.now().minusDays(1), HCP_ID, "Comments", SITE_ID, MSR_ID);

        mockMvc.perform(post(BASE_URL + "/create")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // ── UPDATE ──────────────────────────────────────────────────────────────

    @Test
    void updateVisit_returns200_withUpdatedBody() throws Exception {
        String id = createAndGetId();

        var updateBody = new UpdateVisitInputDTO(
                id, LocalDate.now().minusDays(2), HCP_ID, "Follow-up visit", SITE_ID, MSR_ID, null,
                "Visit date corrected");

        mockMvc.perform(put(BASE_URL + "/update")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.visitComments").value("Follow-up visit"));
    }

    @Test
    void history_showsWhoChangedTheVisitWhenAndWhy_toAdminsOnly() throws Exception {
        String id = createAndGetId();
        var updateBody = new UpdateVisitInputDTO(
                id, LocalDate.now().minusDays(2), HCP_ID, "Follow-up visit", SITE_ID, MSR_ID, null,
                "Comment completed after the visit");
        mockMvc.perform(put(BASE_URL + "/update")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk());

        mockMvc.perform(get(BASE_URL + "/" + id + "/history")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(BASE_URL + "/" + id + "/history")
                        .header("Authorization", "Bearer " + adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].version").value(1))
                .andExpect(jsonPath("$[0].eventType").value("visit.created"))
                .andExpect(jsonPath("$[0].actor").value("testuser"))
                .andExpect(jsonPath("$[1].eventType").value("visit.updated"))
                .andExpect(jsonPath("$[1].reason").value("Comment completed after the visit"))
                .andExpect(jsonPath("$[1].changes[?(@.field == 'visitComments')].to").value("Follow-up visit"));
    }

    private String adminJwt() {
        SecretKeySpec key = new SecretKeySpec(
                JWT_SECRET.getBytes(), 0, JWT_SECRET.getBytes().length, "HmacSHA256");
        return Jwts.builder()
                .subject("auditor")
                .claim("authorities", List.of("ROLE_USER", "ROLE_ADMIN"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(key)
                .compact();
    }

    // ── GET ─────────────────────────────────────────────────────────────────

    @Test
    void getVisitById_returns200_withBody() throws Exception {
        String id = createAndGetId();

        mockMvc.perform(get(BASE_URL + "/" + id)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.healthCareProfId").value(HCP_ID));
    }

    @Test
    void getVisitById_returns400_whenNotFound() throws Exception {
        mockMvc.perform(get(BASE_URL + "/nonexistent-id")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isBadRequest());
    }

    // ── LIST ─────────────────────────────────────────────────────────────────

    @Test
    void listVisits_returns200_withAllVisits() throws Exception {
        createAndGetId();
        createAndGetId();

        mockMvc.perform(post(BASE_URL + "/list")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    // ── ATTACHMENTS ──────────────────────────────────────────────────────────

    @Test
    void uploadAttachments_returns201_withResult() throws Exception {
        String visitId = createAndGetId();

        MockMultipartFile file = new MockMultipartFile(
                "files", "promo.pdf", "application/pdf", "pdf-content".getBytes());

        mockMvc.perform(multipart(BASE_URL + "/" + visitId + "/attachments")
                        .file(file)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.visitId").value(visitId))
                .andExpect(jsonPath("$.attachments", hasSize(1)));
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    /** Incremented each call so successive visits within one test get unique dates. */
    private int visitDayOffset = 0;

    private String createAndGetId() throws Exception {
        visitDayOffset++;
        var body = new CreateVisitInputDTO(
                LocalDate.now().minusDays(visitDayOffset + 2L), HCP_ID, "Test visit", SITE_ID, MSR_ID);

        String response = mockMvc.perform(post(BASE_URL + "/create")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("id").asString();
    }
}
