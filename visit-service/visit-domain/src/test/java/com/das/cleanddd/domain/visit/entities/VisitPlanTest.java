package com.das.cleanddd.domain.visit.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.das.cleanddd.domain.shared.Identifier;
import com.das.cleanddd.domain.shared.TextValueObject;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;

class VisitPlanTest {

    private static final String VISIT_ID = "123e4567-e89b-12d3-a456-426614174001";
    private static final String HCP_ID   = "123e4567-e89b-12d3-a456-426614174002";
    private static final String MSR_ID   = "123e4567-e89b-12d3-a456-426614174003";
    private static final String SITE_ID  = "123e4567-e89b-12d3-a456-426614174004";

    /** Tomorrow at 10:00 — in the future, always valid */
    private static final LocalDateTime VALID_DATE = LocalDateTime.now().plusDays(1).withHour(10);

    private VisitPlan buildValid() throws BusinessValidationException {
        return new VisitPlan(
            new VisitId(VISIT_ID),
            VALID_DATE,
            new HealthCareProfId(HCP_ID),
            new TextValueObject("planned check") {},
            new Identifier(SITE_ID) {},
            List.of(),
            new MedicalSalesRepId(MSR_ID)
        );
    }

    // ------------------------------------------------------------------ //
    @Nested
    class Construction {

        @Test
        void shouldCreateVisitPlanWithAllFields() throws BusinessValidationException {
            VisitPlan plan = buildValid();

            assertThat(plan.visitId().value()).isEqualTo(VISIT_ID);
            assertThat(plan.visitTimeDate()).isEqualTo(VALID_DATE);
            assertThat(plan.healthCareProfId().value()).isEqualTo(HCP_ID);
            assertThat(plan.medicalSalesRepId().value()).isEqualTo(MSR_ID);
            assertThat(plan.visitSideId().value()).isEqualTo(SITE_ID);
            assertThat(plan.visitComments().value()).isEqualTo("planned check");
            assertThat(plan.isActive()).isTrue();
        }

        @Test
        void shouldAllowNullComments() throws BusinessValidationException {
            VisitPlan plan = new VisitPlan(
                new VisitId(VISIT_ID),
                VALID_DATE,
                new HealthCareProfId(HCP_ID),
                null,
                new Identifier(SITE_ID) {},
                List.of(),
                new MedicalSalesRepId(MSR_ID)
            );
            assertThat(plan.visitComments()).isNull();
        }

        @Test
        void shouldAcceptTodayAsValidDate() throws BusinessValidationException {
            // toLocalDate().isBefore(LocalDate.now()) is false for today — must not throw
            LocalDateTime today = LocalDate.now().atTime(23, 59);
            VisitPlan plan = new VisitPlan(
                new VisitId(VISIT_ID),
                today,
                new HealthCareProfId(HCP_ID),
                null,
                new Identifier(SITE_ID) {},
                List.of(),
                new MedicalSalesRepId(MSR_ID)
            );
            assertThat(plan.visitTimeDate().toLocalDate()).isEqualTo(LocalDate.now());
        }

        @Test
        void shouldThrowWhenVisitDateTimeIsNull() {
            assertThatThrownBy(() -> new VisitPlan(
                new VisitId(VISIT_ID),
                null,
                new HealthCareProfId(HCP_ID),
                null,
                new Identifier(SITE_ID) {},
                List.of(),
                new MedicalSalesRepId(MSR_ID)
            )).isInstanceOf(BusinessValidationException.class)
              .hasMessageContaining("required");
        }

        @Test
        void shouldAcceptVisitDateTimeInThePast() throws BusinessValidationException {
            // past-date validation lives in VisitPlanFactory, not in the constructor
            VisitPlan plan = new VisitPlan(
                new VisitId(VISIT_ID),
                LocalDateTime.now().minusDays(1),
                new HealthCareProfId(HCP_ID),
                null,
                new Identifier(SITE_ID) {},
                List.of(),
                new MedicalSalesRepId(MSR_ID)
            );
            assertThat(plan.visitTimeDate()).isBefore(LocalDateTime.now());
        }

        @Test
        void shouldThrowWhenMedicalSalesRepIdIsNull() {
            assertThatThrownBy(() -> new VisitPlan(
                new VisitId(VISIT_ID),
                VALID_DATE,
                new HealthCareProfId(HCP_ID),
                null,
                new Identifier(SITE_ID) {},
                List.of(),
                null
            )).isInstanceOf(BusinessValidationException.class)
              .hasMessageContaining("Medical Sales Representative is required");
        }

        @Test
        void shouldThrowWhenHealthCareProfIdIsNull() {
            assertThatThrownBy(() -> new VisitPlan(
                new VisitId(VISIT_ID),
                VALID_DATE,
                null,
                null,
                new Identifier(SITE_ID) {},
                List.of(),
                new MedicalSalesRepId(MSR_ID)
            )).isInstanceOf(BusinessValidationException.class)
              .hasMessageContaining("Health Care Professional is required");
        }
    }

    // ------------------------------------------------------------------ //
    @Nested
    class DayPeriod {

        @Test
        void shouldReturnMorningForHourBeforeNoon() throws BusinessValidationException {
            VisitPlan plan = new VisitPlan(
                new VisitId(VISIT_ID),
                VALID_DATE.withHour(9),
                new HealthCareProfId(HCP_ID),
                null,
                new Identifier(SITE_ID) {},
                List.of(),
                new MedicalSalesRepId(MSR_ID)
            );
            assertThat(plan.visitDayPeriod()).isEqualTo("MORNING");
        }

        @Test
        void shouldReturnAfternoonForHourAtNoon() throws BusinessValidationException {
            VisitPlan plan = new VisitPlan(
                new VisitId(VISIT_ID),
                VALID_DATE.withHour(12),
                new HealthCareProfId(HCP_ID),
                null,
                new Identifier(SITE_ID) {},
                List.of(),
                new MedicalSalesRepId(MSR_ID)
            );
            assertThat(plan.visitDayPeriod()).isEqualTo("AFTERNOON");
        }

        @Test
        void shouldReturnAfternoonForHourAfterNoon() throws BusinessValidationException {
            VisitPlan plan = new VisitPlan(
                new VisitId(VISIT_ID),
                VALID_DATE.withHour(17),
                new HealthCareProfId(HCP_ID),
                null,
                new Identifier(SITE_ID) {},
                List.of(),
                new MedicalSalesRepId(MSR_ID)
            );
            assertThat(plan.visitDayPeriod()).isEqualTo("AFTERNOON");
        }
    }

    // ------------------------------------------------------------------ //
    @Nested
    class ItemManagement {

        @Test
        void shouldAddItemToVisitPlan() throws BusinessValidationException {
            VisitPlan plan = buildValid();
            VisitItem item = new VisitItem();
            plan.addItem(item);
            plan.removeItem(item); // should not throw
        }

        @Test
        void shouldRemoveItemFromVisitPlan() throws BusinessValidationException {
            VisitPlan plan = buildValid();
            VisitItem item = new VisitItem();
            plan.addItem(item);
            plan.removeItem(item);
        }

        @Test
        void shouldDeactivateVisitPlan() throws BusinessValidationException {
            VisitPlan plan = buildValid();

            plan.deactivate();

            assertThat(plan.isActive()).isFalse();
        }
    }

    // ------------------------------------------------------------------ //
    @Nested
    class EqualityAndIdentity {

        @Test
        void shouldBeEqualWhenSameIdAndDate() throws BusinessValidationException {
            VisitPlan p1 = buildValid();
            VisitPlan p2 = buildValid();
            assertThat(p1).isEqualTo(p2);
        }

        @Test
        void shouldNotBeEqualWhenDifferentDate() throws BusinessValidationException {
            VisitPlan p1 = buildValid();
            VisitPlan p2 = new VisitPlan(
                new VisitId(VISIT_ID),
                VALID_DATE.plusDays(1),
                new HealthCareProfId(HCP_ID),
                null,
                new Identifier(SITE_ID) {},
                List.of(),
                new MedicalSalesRepId(MSR_ID)
            );
            assertThat(p1).isNotEqualTo(p2);
        }

        @Test
        void shouldUseVisitIdAndDateForHashCode() throws BusinessValidationException {
            VisitPlan p1 = buildValid();
            VisitPlan p2 = buildValid();
            assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
        }
    }
}
