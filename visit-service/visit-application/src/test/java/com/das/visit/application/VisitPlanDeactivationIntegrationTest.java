package com.das.visit.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.das.cleanddd.domain.shared.Identifier;
import com.das.cleanddd.domain.shared.TextValueObject;
import com.das.cleanddd.domain.visit.IVisitPlanRepository;
import com.das.cleanddd.domain.visit.entities.HealthCareProfId;
import com.das.cleanddd.domain.visit.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.visit.entities.VisitDateTime;
import com.das.cleanddd.domain.visit.entities.VisitId;
import com.das.cleanddd.domain.visit.entities.VisitPlan;
import com.das.cleanddd.domain.visit.ports.IHealthCareProfValidator;
import com.das.cleanddd.domain.visit.ports.IMedicalSalesRepValidator;
import com.das.cleanddd.domain.visit.ports.IProductPromoAttachmentStorage;
import com.das.infra.service.visit.HcpEventMessage;
import com.das.infra.service.visit.HcpSnapshotUpdater;
import com.das.infra.service.visit.MsrEventMessage;
import com.das.infra.service.visit.MsrSnapshotUpdater;
import com.das.infra.service.visit.VisitPlanJpaRepository;

/**
 * Verifies that upstream deactivation events deactivate future VisitPlans.
 * RabbitMQ is disabled in tests, so the listener methods are invoked directly
 * against the real Spring beans and the real SQLServerVisitPlanRepository.
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
class VisitPlanDeactivationIntegrationTest {

    private static final String VISIT_ID = "99999999-9999-9999-9999-999999999999";
    private static final String HCP_ID = "11111111-1111-1111-1111-111111111111";
    private static final String MSR_ID = "22222222-2222-2222-2222-222222222222";
    private static final String SITE_ID = "33333333-3333-3333-3333-333333333333";

    @Autowired
    private IVisitPlanRepository visitPlanRepository;

    @Autowired
    private VisitPlanJpaRepository visitPlanJpaRepository;

    @Autowired
    private MsrSnapshotUpdater msrSnapshotUpdater;

    @Autowired
    private HcpSnapshotUpdater hcpSnapshotUpdater;

    @MockitoBean
    private ConnectionFactory connectionFactory;

    @MockitoBean
    private IHealthCareProfValidator healthCareProfValidator;

    @MockitoBean
    private IMedicalSalesRepValidator medicalSalesRepValidator;

    @MockitoBean
    private IProductPromoAttachmentStorage attachmentStorage;

    @AfterEach
    void cleanUp() {
        visitPlanJpaRepository.deleteAll();
    }

    @Test
    void msrDeactivatedEvent_deactivatesFutureVisitPlan() throws Exception {
        VisitPlan futurePlan = buildFutureVisitPlan();
        visitPlanRepository.save(futurePlan);

        msrSnapshotUpdater.onMsrEvent(new MsrEventMessage(
                "MSR_DEACTIVATED",
                MSR_ID,
                null,
                null,
                null,
                false,
                LocalDateTime.now().toString()));

        VisitPlan saved = visitPlanRepository.search(new VisitId(VISIT_ID)).orElseThrow();
        assertThat(saved.isActive()).isFalse();
    }

    @Test
    void hcpDeactivatedEvent_deactivatesFutureVisitPlan() throws Exception {
        VisitPlan futurePlan = buildFutureVisitPlan();
        visitPlanRepository.save(futurePlan);

        hcpSnapshotUpdater.onHcpEvent(new HcpEventMessage(
                "HCP_DEACTIVATED",
                HCP_ID,
                null,
                null,
                null,
                false,
                LocalDateTime.now().toString()));

        VisitPlan saved = visitPlanRepository.search(new VisitId(VISIT_ID)).orElseThrow();
        assertThat(saved.isActive()).isFalse();
    }

    private VisitPlan buildFutureVisitPlan() throws Exception {
        return new VisitPlan(
                new VisitId(VISIT_ID),
                new VisitDateTime(LocalDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0)),
                new HealthCareProfId(HCP_ID),
                new TextValueObject("integration test") {},
                new Identifier(SITE_ID) {},
                List.of(),
                new MedicalSalesRepId(MSR_ID));
    }
}