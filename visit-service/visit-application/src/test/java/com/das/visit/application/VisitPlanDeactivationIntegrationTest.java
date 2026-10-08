package com.das.visit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
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
import com.das.infra.service.visit.VisitPlanJpaRepository;
import com.das.infra.service.visit.events.HcpEventListener;
import com.das.infra.service.visit.events.MsrEventListener;

/**
 * Verifies that upstream deactivation events deactivate future VisitPlans.
 * RabbitMQ is disabled in tests, so the listener methods are invoked directly
 * against the real Spring beans and the real SQLServerVisitPlanRepository. HCP events go
 * through the full inbound path: raw AMQP message → anti-corruption translator → handler
 * (the same for MSR events).
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
    private MsrEventListener msrEventListener;

    @Autowired
    private HcpEventListener hcpEventListener;

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

        msrEventListener.onMessage(amqpMessage("""
                {"eventId":"%s","eventType":"msr.deactivated","schemaVersion":1,"aggregateId":"%s",
                 "aggregateVersion":1,"occurredAt":"2026-10-08T10:00:00Z","producer":"medical-sales-rep-service",
                 "data":{"active":false}}
                """.formatted(UUID.randomUUID(), MSR_ID)));

        VisitPlan saved = visitPlanRepository.search(new VisitId(VISIT_ID)).orElseThrow();
        assertThat(saved.isActive()).isFalse();
    }

    @Test
    void hcpDeactivatedEvent_deactivatesFutureVisitPlan() throws Exception {
        VisitPlan futurePlan = buildFutureVisitPlan();
        visitPlanRepository.save(futurePlan);

        hcpEventListener.onMessage(amqpMessage("""
                {"eventId":"%s","eventType":"hcp.deactivated","schemaVersion":1,"aggregateId":"%s",
                 "aggregateVersion":1,"occurredAt":"2026-10-08T10:00:00Z","producer":"healthcare-prof-service",
                 "data":{"active":false}}
                """.formatted(UUID.randomUUID(), HCP_ID)));

        VisitPlan saved = visitPlanRepository.search(new VisitId(VISIT_ID)).orElseThrow();
        assertThat(saved.isActive()).isFalse();
    }

    @Test
    void invalidHcpEvent_isRejectedForTheDeadLetterQueueAndChangesNothing() throws Exception {
        VisitPlan futurePlan = buildFutureVisitPlan();
        visitPlanRepository.save(futurePlan);

        assertThatThrownBy(() -> hcpEventListener.onMessage(amqpMessage(
                "{\"eventType\":\"HCP_DEACTIVATED\",\"id\":\"" + HCP_ID + "\",\"active\":false}")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        VisitPlan saved = visitPlanRepository.search(new VisitId(VISIT_ID)).orElseThrow();
        assertThat(saved.isActive()).isTrue();
    }

    private static Message amqpMessage(String json) {
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setMessageId(UUID.randomUUID().toString());
        return new Message(json.getBytes(StandardCharsets.UTF_8), props);
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