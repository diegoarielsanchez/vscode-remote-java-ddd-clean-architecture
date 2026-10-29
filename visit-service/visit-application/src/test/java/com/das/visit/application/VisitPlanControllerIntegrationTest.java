package com.das.visit.application;

import com.das.cleanddd.domain.visit.ports.IHealthCareProfValidator;
import com.das.cleanddd.domain.visit.ports.IMedicalSalesRepValidator;
import com.das.cleanddd.domain.visit.ports.IProductPromoAttachmentStorage;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import com.das.cleanddd.domain.visit.usecases.dtos.CreateVisitPlanInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.UpdateVisitPlanInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitItemDTO;
import com.das.infra.service.visit.VisitPlanJpaRepository;
import tools.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

    @Autowired
    private com.das.infra.service.visit.VisitJpaRepository visitJpaRepository;

        @MockitoBean
    private ConnectionFactory connectionFactory;

        @MockitoBean
    private IHealthCareProfValidator healthCareProfValidator;

        @MockitoBean
    private IMedicalSalesRepValidator medicalSalesRepValidator;

        @MockitoBean
    private IProductPromoAttachmentStorage attachmentStorage;

    @MockitoBean
    private com.das.cleanddd.domain.visit.ports.IProductAvailability productAvailability;

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
        visitJpaRepository.deleteAll();
    }

    // ── Ownership: a plan always belongs to the caller's own MSR (OWASP A01) ──

    private static final String OTHER_MSR_ID = "dddddddd-dddd-dddd-dddd-dddddddddddd";

    @Test
    void createVisitPlan_returns403_whenCallerIsNotLinkedToAnMsr() throws Exception {
        var body = new CreateVisitPlanInputDTO(LocalDateTime.now().plusDays(7), HCP_ID, null, SITE_ID, null);

        mockMvc.perform(post(BASE_URL + "/create")
                        .header("Authorization", "Bearer " + jwtFor(null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createVisitPlan_returns403_whenBodyNamesAnotherMsr() throws Exception {
        var body = new CreateVisitPlanInputDTO(LocalDateTime.now().plusDays(7), HCP_ID, null, SITE_ID, OTHER_MSR_ID);

        mockMvc.perform(post(BASE_URL + "/create")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createVisitPlan_usesCallersMsr_whenBodyOmitsIt() throws Exception {
        var body = new CreateVisitPlanInputDTO(LocalDateTime.now().plusDays(7), HCP_ID, null, SITE_ID, null);

        mockMvc.perform(post(BASE_URL + "/create")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.medicalSalesRepId").value(MSR_ID));
    }

    @Test
    void updateVisitPlan_cannotTouchAnotherMsrsPlan() throws Exception {
        String id = createAndGetId();
        var body = new UpdateVisitPlanInputDTO(id, LocalDateTime.now().plusDays(9), HCP_ID, "hijack", SITE_ID, null);

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

    // ── Items: the products and quantities to bring ──

    private static final String PRODUCT_A = "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee1";
    private static final String PRODUCT_B = "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee2";

    private String createWithItems(List<VisitItemDTO> items) throws Exception {
        var body = new CreateVisitPlanInputDTO(LocalDateTime.now().plusDays(3), HCP_ID, null, SITE_ID, null, items);
        String response = mockMvc.perform(post(BASE_URL + "/create")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asString();
    }

    private void update(String id, List<VisitItemDTO> items) throws Exception {
        var body = new UpdateVisitPlanInputDTO(id, LocalDateTime.now().plusDays(4), HCP_ID, "moved", SITE_ID, null, items,
                "HCP asked to move the visit");
        mockMvc.perform(put(BASE_URL + "/update")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    void createVisitPlan_storesItems_addingUpRepeatedProducts() throws Exception {
        String id = createWithItems(List.of(
                new VisitItemDTO(PRODUCT_A, 2), new VisitItemDTO(PRODUCT_B, 1), new VisitItemDTO(PRODUCT_A, 3)));

        mockMvc.perform(get(BASE_URL + "/" + id).header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[?(@.productId == '" + PRODUCT_A + "')].quantity").value(5))
                .andExpect(jsonPath("$.items[?(@.productId == '" + PRODUCT_B + "')].quantity").value(1));
    }

    @Test
    void updateVisitPlan_withoutItemsKeepsThem_andAnEmptyListClearsThem() throws Exception {
        String id = createWithItems(List.of(new VisitItemDTO(PRODUCT_A, 4)));

        update(id, null);
        mockMvc.perform(get(BASE_URL + "/" + id).header("Authorization", "Bearer " + authToken))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(4));

        update(id, List.of());
        mockMvc.perform(get(BASE_URL + "/" + id).header("Authorization", "Bearer " + authToken))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void createVisitPlan_returns400_forAnOutOfRangeQuantity() throws Exception {
        var body = new CreateVisitPlanInputDTO(LocalDateTime.now().plusDays(3), HCP_ID, null, SITE_ID, null,
                List.of(new VisitItemDTO(PRODUCT_A, 0)));

        mockMvc.perform(post(BASE_URL + "/create")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // ── Suggestions: read-only drafts from the MSR's own visit history ──

    private void storeVisit(String msrId, LocalDateTime at) {
        storeVisit(msrId, at, List.of());
    }

    private void storeVisit(String msrId, LocalDateTime at, List<com.das.infra.service.visit.VisitItemEmbeddable> items) {
        var visit = new com.das.infra.service.visit.VisitEntity();
        visit.setItems(items);
        visit.setId(java.util.UUID.randomUUID().toString());
        visit.setVisitDate(at);
        visit.setHealthCareProfId(HCP_ID);
        visit.setMedicalSalesRepId(msrId);
        visit.setVisitSiteId(SITE_ID);
        visitJpaRepository.save(visit);
    }

    @Test
    void suggestions_draftNextMonthFromTheCallersOwnVisits_withoutSavingAnything() throws Exception {
        storeVisit(MSR_ID, LocalDateTime.now().minusDays(20));
        storeVisit(OTHER_MSR_ID, LocalDateTime.now().minusDays(20)); // another rep's history is not used

        mockMvc.perform(get(BASE_URL + "/suggestions").header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.medicalSalesRepId").value(MSR_ID))
                .andExpect(jsonPath("$.month").value(java.time.YearMonth.now().plusMonths(1).toString()))
                .andExpect(jsonPath("$.suggestions.length()").value(1))
                .andExpect(jsonPath("$.suggestions[0].healthCareProfId").value(HCP_ID))
                .andExpect(jsonPath("$.suggestions[0].visitSiteId").value(SITE_ID));

        org.junit.jupiter.api.Assertions.assertEquals(0, visitPlanJpaRepository.count(), "suggestions must not create plans");
    }

    @Test
    void suggestions_listTheProductsTheHcpUsuallyReceives_withAStockFlag() throws Exception {
        storeVisit(MSR_ID, LocalDateTime.now().minusDays(20),
                List.of(new com.das.infra.service.visit.VisitItemEmbeddable(PRODUCT_A, 4)));
        when(productAvailability.isAvailable(PRODUCT_A, 4)).thenReturn(java.util.Optional.of(true));

        mockMvc.perform(get(BASE_URL + "/suggestions").header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0].items.length()").value(1))
                .andExpect(jsonPath("$.suggestions[0].items[0].productId").value(PRODUCT_A))
                .andExpect(jsonPath("$.suggestions[0].items[0].quantity").value(4))
                .andExpect(jsonPath("$.suggestions[0].items[0].inStock").value(true));
    }

    @Test
    void suggestions_forAnotherMsr_return403() throws Exception {
        mockMvc.perform(get(BASE_URL + "/suggestions").param("medicalSalesRepId", OTHER_MSR_ID)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void suggestions_callerNotLinkedToAnMsr_return403() throws Exception {
        mockMvc.perform(get(BASE_URL + "/suggestions").header("Authorization", "Bearer " + jwtFor(null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void suggestions_agentMustNameTheMsr() throws Exception {
        mockMvc.perform(get(BASE_URL + "/suggestions").header("Authorization", "Bearer " + agentJwt()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(BASE_URL + "/suggestions").param("medicalSalesRepId", OTHER_MSR_ID)
                        .header("Authorization", "Bearer " + agentJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.medicalSalesRepId").value(OTHER_MSR_ID));
    }

    @Test
    void suggestions_forAPastMonth_areRefused() throws Exception {
        int status = mockMvc.perform(get(BASE_URL + "/suggestions")
                        .param("month", java.time.YearMonth.now().minusMonths(1).toString())
                        .header("Authorization", "Bearer " + authToken))
                .andReturn().getResponse().getStatus();
        org.junit.jupiter.api.Assertions.assertTrue(status >= 400 && status < 500, "expected a 4xx, got " + status);
    }

    // ── Order draft: the month's products, added up from active visit plans ──

    private void planNextMonth(String token, int day, List<VisitItemDTO> items) throws Exception {
        LocalDateTime at = java.time.YearMonth.now().plusMonths(1).atDay(day).atTime(9, 0);
        var body = new CreateVisitPlanInputDTO(at, HCP_ID, null, SITE_ID, null, items);
        mockMvc.perform(post(BASE_URL + "/create")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    @Test
    void orderDraft_addsUpTheCallersPlansForNextMonth_withoutOrderingAnything() throws Exception {
        planNextMonth(authToken, 10, List.of(new VisitItemDTO(PRODUCT_A, 4), new VisitItemDTO(PRODUCT_B, 1)));
        planNextMonth(authToken, 11, List.of(new VisitItemDTO(PRODUCT_A, 6)));
        planNextMonth(jwtFor(OTHER_MSR_ID), 12, List.of(new VisitItemDTO(PRODUCT_A, 100))); // another rep's plan
        when(productAvailability.isAvailable(PRODUCT_A, 10)).thenReturn(java.util.Optional.of(true));
        when(productAvailability.isAvailable(PRODUCT_B, 1)).thenReturn(java.util.Optional.of(false));

        mockMvc.perform(get(BASE_URL + "/order-draft").header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.medicalSalesRepId").value(MSR_ID))
                .andExpect(jsonPath("$.plannedVisits").value(2))
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.lines[0].productId").value(PRODUCT_A))
                .andExpect(jsonPath("$.lines[0].quantity").value(10))
                .andExpect(jsonPath("$.lines[0].plannedVisits").value(2))
                .andExpect(jsonPath("$.lines[0].inStock").value(true))
                .andExpect(jsonPath("$.lines[1].inStock").value(false))
                // PRODUCT_B is short, so the ready-to-submit order leaves it out
                .andExpect(jsonPath("$.orderRequests.length()").value(1))
                .andExpect(jsonPath("$.orderRequests[0].lines.length()").value(1))
                .andExpect(jsonPath("$.orderRequests[0].lines[0].productId").value(PRODUCT_A))
                .andExpect(jsonPath("$.orderRequests[0].lines[0].quantity").value(10));
    }

    @Test
    void orderDraft_forAnotherMsr_return403_andTheAgentMustNameTheMsr() throws Exception {
        mockMvc.perform(get(BASE_URL + "/order-draft").param("medicalSalesRepId", OTHER_MSR_ID)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(BASE_URL + "/order-draft").header("Authorization", "Bearer " + agentJwt()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE_URL + "/order-draft").param("medicalSalesRepId", MSR_ID)
                        .header("Authorization", "Bearer " + agentJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.medicalSalesRepId").value(MSR_ID));
    }

    // ── JWT helper ──────────────────────────────────────────────────────────

    private String generateValidJwt() {
        return jwtFor(MSR_ID);
    }

    /** A ROLE_USER token for the given MSR, or with no msr_id claim when {@code msrId} is null. */
    private String jwtFor(String msrId) {
        return jwtFor(msrId, List.of("ROLE_USER"));
    }

    /** The AI Agent Service: no msr_id of its own. */
    private String agentJwt() {
        return jwtFor(null, List.of("ROLE_USER", "ROLE_AGENT"));
    }

    private String jwtFor(String msrId, List<String> authorities) {
        SecretKeySpec key = new SecretKeySpec(
                JWT_SECRET.getBytes(), 0, JWT_SECRET.getBytes().length, "HmacSHA256");
        return Jwts.builder()
                .subject("testuser")
                .claim("authorities", authorities)
                .claim("msr_id", msrId) // visit plans always belong to the caller's own MSR
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
                HCP_ID, "Rescheduled appointment", SITE_ID, MSR_ID, null, "HCP asked to move the visit");

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

        return objectMapper.readTree(response).get("id").asString();
    }
}
