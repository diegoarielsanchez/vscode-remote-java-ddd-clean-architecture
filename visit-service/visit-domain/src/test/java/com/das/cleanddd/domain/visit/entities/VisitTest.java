package com.das.cleanddd.domain.visit.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.das.cleanddd.domain.shared.AddressValueObject;
import com.das.cleanddd.domain.shared.Identifier;
import com.das.cleanddd.domain.shared.LargeFileValueObject;
import com.das.cleanddd.domain.shared.TextValueObject;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;

class VisitTest {

    private static final String VISIT_ID  = "123e4567-e89b-12d3-a456-426614174001";
    private static final String HCP_ID    = "123e4567-e89b-12d3-a456-426614174002";
    private static final String MSR_ID    = "123e4567-e89b-12d3-a456-426614174003";
    private static final String SITE_ID   = "123e4567-e89b-12d3-a456-426614174004";

    /** Yesterday at 10:00 — in the past and within 1 month window */
    private static final LocalDateTime VALID_DATE = LocalDateTime.now().minusDays(1).withHour(10);

    /** Creates a minimal valid {@link LargeFileValueObject} with 4-byte PNG magic bytes. */
    private static LargeFileValueObject buildAttachment(String fileName) {
        byte[] content = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}; // PNG magic bytes
        return LargeFileValueObject.of(fileName, "image/png", content.length, content);
    }

    private Visit buildValid() throws BusinessValidationException {
        return new Visit(
            new VisitId(VISIT_ID),
            new VisitDateTime(VALID_DATE),
            new HealthCareProfId(HCP_ID),
            new TextValueObject("routine check") {},
            new Identifier(SITE_ID) {},
            List.of(),
            new MedicalSalesRepId(MSR_ID)
        );
    }

    // ------------------------------------------------------------------ //
    @Nested
    class Construction {

        @Test
        void shouldCreateVisitWithAllFields() throws BusinessValidationException {
            Visit visit = buildValid();

            assertThat(visit.visitId().value()).isEqualTo(VISIT_ID);
            assertThat(visit.visitDate().value()).isEqualTo(VALID_DATE);
            assertThat(visit.healthCareProfId().value()).isEqualTo(HCP_ID);
            assertThat(visit.medicalSalesRepId().value()).isEqualTo(MSR_ID);
            assertThat(visit.visitSideId().value()).isEqualTo(SITE_ID);
            assertThat(visit.visitComments().value()).isEqualTo("routine check");
        }

        @Test
        void shouldAllowNullComments() throws BusinessValidationException {
            Visit visit = new Visit(
                new VisitId(VISIT_ID),
                new VisitDateTime(VALID_DATE),
                new HealthCareProfId(HCP_ID),
                null,
                new Identifier(SITE_ID) {},
                List.of(),
                new MedicalSalesRepId(MSR_ID)
            );
            assertThat(visit.visitComments()).isNull();
        }

        @Test
        void shouldThrowWhenVisitDateIsNull() {
            assertThatThrownBy(() -> new Visit(
                new VisitId(VISIT_ID),
                null,
                new HealthCareProfId(HCP_ID),
                null,
                new Identifier(SITE_ID) {},
                List.of(),
                new MedicalSalesRepId(MSR_ID)
            )).isInstanceOf(BusinessValidationException.class);
        }

        @Test
        void shouldThrowWhenVisitDateIsInTheFuture() {
            assertThatThrownBy(() -> new Visit(
                new VisitId(VISIT_ID),
                new VisitDateTime(LocalDateTime.now().plusDays(1)),
                new HealthCareProfId(HCP_ID),
                null,
                new Identifier(SITE_ID) {},
                List.of(),
                new MedicalSalesRepId(MSR_ID)
            )).isInstanceOf(BusinessValidationException.class)
              .hasMessageContaining("later than today");
        }

        @Test
        void shouldThrowWhenVisitDateIsMoreThanOneMonthAgo() {
            assertThatThrownBy(() -> new Visit(
                new VisitId(VISIT_ID),
                new VisitDateTime(LocalDateTime.now().minusMonths(2)),
                new HealthCareProfId(HCP_ID),
                null,
                new Identifier(SITE_ID) {},
                List.of(),
                new MedicalSalesRepId(MSR_ID)
            )).isInstanceOf(BusinessValidationException.class)
              .hasMessageContaining("more than one month in the past");
        }

        @Test
        void shouldThrowWhenMedicalSalesRepIdIsNull() {
            assertThatThrownBy(() -> new Visit(
                new VisitId(VISIT_ID),
                new VisitDateTime(VALID_DATE),
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
            assertThatThrownBy(() -> new Visit(
                new VisitId(VISIT_ID),
                new VisitDateTime(VALID_DATE),
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
            Visit visit = new Visit(
                new VisitId(VISIT_ID),
                new VisitDateTime(VALID_DATE.withHour(9)),
                new HealthCareProfId(HCP_ID),
                null,
                new Identifier(SITE_ID) {},
                List.of(),
                new MedicalSalesRepId(MSR_ID)
            );
            assertThat(visit.visitDayPeriod()).isEqualTo("MORNING");
        }

        @Test
        void shouldReturnAfternoonForHourAtNoon() throws BusinessValidationException {
            Visit visit = new Visit(
                new VisitId(VISIT_ID),
                new VisitDateTime(VALID_DATE.withHour(12)),
                new HealthCareProfId(HCP_ID),
                null,
                new Identifier(SITE_ID) {},
                List.of(),
                new MedicalSalesRepId(MSR_ID)
            );
            assertThat(visit.visitDayPeriod()).isEqualTo("AFTERNOON");
        }

        @Test
        void shouldReturnAfternoonForHourAfterNoon() throws BusinessValidationException {
            Visit visit = new Visit(
                new VisitId(VISIT_ID),
                new VisitDateTime(VALID_DATE.withHour(15)),
                new HealthCareProfId(HCP_ID),
                null,
                new Identifier(SITE_ID) {},
                List.of(),
                new MedicalSalesRepId(MSR_ID)
            );
            assertThat(visit.visitDayPeriod()).isEqualTo("AFTERNOON");
        }
    }

    // ------------------------------------------------------------------ //
    @Nested
    class ItemManagement {

        @Test
        void shouldAddItemToVisit() throws BusinessValidationException {
            Visit visit = buildValid();
            VisitItem item = new VisitItem();
            visit.addItem(item);
            // No direct accessor — verify indirectly by removing the same item
            visit.removeItem(item); // should not throw
        }

        @Test
        void shouldRemoveItemFromVisit() throws BusinessValidationException {
            Visit visit = buildValid();
            VisitItem item = new VisitItem();
            visit.addItem(item);
            visit.removeItem(item); // should not throw
        }
    }

    // ------------------------------------------------------------------ //
    @Nested
    class ProductPromoAttachments {

        @Test
        void shouldStartWithEmptyProductPromoAttachments() throws BusinessValidationException {
            Visit visit = buildValid();
            assertThat(visit.productPromoAttachments()).isEmpty();
        }

        @Test
        void shouldAddOneAttachment() throws BusinessValidationException {
            Visit visit = buildValid();
            LargeFileValueObject attachment = buildAttachment("promo.png");

            visit.addProductPromoAttachment(attachment);

            assertThat(visit.productPromoAttachments()).hasSize(1);
            assertThat(visit.productPromoAttachments().get(0).fileName()).isEqualTo("promo.png");
            assertThat(visit.productPromoAttachments().get(0).contentType()).isEqualTo("image/png");
        }

        @Test
        void shouldAddMultipleAttachments() throws BusinessValidationException {
            Visit visit = buildValid();

            visit.addProductPromoAttachment(buildAttachment("promo-a.png"));
            visit.addProductPromoAttachment(buildAttachment("promo-b.png"));

            assertThat(visit.productPromoAttachments()).hasSize(2);
        }

        @Test
        void shouldRemoveAttachment() throws BusinessValidationException {
            Visit visit = buildValid();
            LargeFileValueObject attachment = buildAttachment("promo.png");
            visit.addProductPromoAttachment(attachment);

            visit.removeProductPromoAttachment(attachment);

            assertThat(visit.productPromoAttachments()).isEmpty();
        }

        @Test
        void shouldThrowWhenAddingNullAttachment() throws BusinessValidationException {
            Visit visit = buildValid();

            assertThatThrownBy(() -> visit.addProductPromoAttachment(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
        }

        @Test
        void shouldReturnUnmodifiableList() throws BusinessValidationException {
            Visit visit = buildValid();
            visit.addProductPromoAttachment(buildAttachment("promo.png"));

            List<LargeFileValueObject> list = visit.productPromoAttachments();

            assertThatThrownBy(() -> list.add(buildAttachment("extra.png")))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void shouldPreserveSha256HashOnAttachment() throws BusinessValidationException {
            Visit visit = buildValid();
            LargeFileValueObject attachment = buildAttachment("promo.png");

            visit.addProductPromoAttachment(attachment);

            assertThat(visit.productPromoAttachments().get(0).sha256Hash())
                .isNotBlank()
                .hasSize(64); // SHA-256 hex = 64 chars
        }
    }

    // ------------------------------------------------------------------ //
    @Nested
    class EqualityAndIdentity {

        @Test
        void shouldBeEqualWhenSameIdAndDate() throws BusinessValidationException {
            Visit v1 = buildValid();
            Visit v2 = buildValid();
            assertThat(v1).isEqualTo(v2);
        }

        @Test
        void shouldNotBeEqualWhenDifferentDate() throws BusinessValidationException {
            Visit v1 = buildValid();
            Visit v2 = new Visit(
                new VisitId(VISIT_ID),
                new VisitDateTime(VALID_DATE.minusDays(1)),
                new HealthCareProfId(HCP_ID),
                null,
                new Identifier(SITE_ID) {},
                List.of(),
                new MedicalSalesRepId(MSR_ID)
            );
            assertThat(v1).isNotEqualTo(v2);
        }

        @Test
        void shouldUseVisitIdAndDateForHashCode() throws BusinessValidationException {
            Visit v1 = buildValid();
            Visit v2 = buildValid();
            assertThat(v1.hashCode()).isEqualTo(v2.hashCode());
        }
    }

    // ------------------------------------------------------------------ //
    @Nested
    class Address {

        private final AddressValueObject site = new AddressValueObject(
                "1 Clinic Rd", "Springfield", "IL", "62701", "USA");

        @Test
        void shouldDefaultToNoAddress() throws BusinessValidationException {
            assertThat(buildValid().address()).isNull();
        }

        @Test
        void shouldExposeAddressItWasConstructedWith() throws BusinessValidationException {
            Visit visit = new Visit(
                    new VisitId(VISIT_ID), new VisitDateTime(VALID_DATE), new HealthCareProfId(HCP_ID),
                    new TextValueObject("routine check") {}, new Identifier(SITE_ID) {}, List.of(),
                    new MedicalSalesRepId(MSR_ID), site);

            assertThat(visit.address()).isEqualTo(site);
        }

        @Test
        void updateWithoutAddressArgumentShouldNotTouchExistingAddress() throws BusinessValidationException {
            Visit visit = new Visit(
                    new VisitId(VISIT_ID), new VisitDateTime(VALID_DATE), new HealthCareProfId(HCP_ID),
                    new TextValueObject("routine check") {}, new Identifier(SITE_ID) {}, List.of(),
                    new MedicalSalesRepId(MSR_ID), site);

            visit.update(new VisitDateTime(VALID_DATE), new HealthCareProfId(HCP_ID),
                    new TextValueObject("updated") {}, new Identifier(SITE_ID) {}, new MedicalSalesRepId(MSR_ID));

            assertThat(visit.address()).isEqualTo(site);
        }

        @Test
        void updateWithAddressArgumentShouldReplaceIt() throws BusinessValidationException {
            Visit visit = buildValid();
            AddressValueObject newSite = new AddressValueObject("2 Clinic Rd", "Springfield", "IL", "62701", "USA");

            visit.update(new VisitDateTime(VALID_DATE), new HealthCareProfId(HCP_ID),
                    new TextValueObject("updated") {}, new Identifier(SITE_ID) {}, new MedicalSalesRepId(MSR_ID),
                    newSite);

            assertThat(visit.address()).isEqualTo(newSite);
        }

        @Test
        void reconstructShouldRehydrateAddress() {
            Visit visit = Visit.reconstruct(
                    new VisitId(VISIT_ID), new VisitDateTime(VALID_DATE), new HealthCareProfId(HCP_ID),
                    new TextValueObject("routine check") {}, new Identifier(SITE_ID) {}, List.of(),
                    new MedicalSalesRepId(MSR_ID), site);

            assertThat(visit.address()).isEqualTo(site);
        }
    }
}
