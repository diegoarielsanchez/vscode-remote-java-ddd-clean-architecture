package com.das.cleanddd.domain.visit.usecases.services;

import com.das.cleanddd.domain.visit.usecases.dtos.SuggestedVisitItemDTO;
import com.das.cleanddd.domain.visit.ports.IProductAvailability;
import com.das.cleanddd.domain.visit.entities.VisitItem;
import java.util.Optional;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.das.cleanddd.domain.shared.Identifier;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import com.das.cleanddd.domain.visit.IVisitPlanRepository;
import com.das.cleanddd.domain.visit.IVisitRepository;
import com.das.cleanddd.domain.visit.entities.HealthCareProfId;
import com.das.cleanddd.domain.visit.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.visit.entities.Visit;
import com.das.cleanddd.domain.visit.entities.VisitDateTime;
import com.das.cleanddd.domain.visit.entities.VisitId;
import com.das.cleanddd.domain.visit.entities.VisitPlan;
import com.das.cleanddd.domain.visit.ports.IHealthCareProfValidator;
import com.das.cleanddd.domain.visit.usecases.dtos.SuggestVisitPlansInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitPlanSuggestionDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitPlanSuggestionsOutputDTO;

/**
 * "Today" is Saturday 2026-10-17, so next month is November 2026, which starts on a Sunday:
 * the first plannable slot is Monday 2026-11-02 at 09:00.
 */
@ExtendWith(MockitoExtension.class)
class SuggestVisitPlansUseCaseTest {

    private static final Clock TODAY = Clock.fixed(
            LocalDateTime.of(2026, 10, 17, 12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);
    private static final String MSR_ID = "823e4567-e89b-12d3-a456-426614174000";
    private static final String SITE_ID = "823e4567-e89b-12d3-a456-426614174999";

    @Mock private IVisitRepository visitRepository;
    @Mock private IVisitPlanRepository visitPlanRepository;
    @Mock private IHealthCareProfValidator healthCareProfValidator;
    @Mock private IProductAvailability productAvailability;

    private SuggestVisitPlansUseCase useCase;
    private final List<Visit> history = new ArrayList<>();
    private final List<VisitPlan> novemberPlans = new ArrayList<>();

    @BeforeEach
    void setUp() {
        useCase = new SuggestVisitPlansUseCase(visitRepository, visitPlanRepository, healthCareProfValidator,
                productAvailability, TODAY);
        lenient().when(productAvailability.isAvailable(anyString(), anyInt())).thenReturn(Optional.of(true));
        lenient().when(visitRepository.findByMedicalSalesRepIdSince(any(), any())).thenReturn(history);
        lenient().when(visitPlanRepository.findActiveByMedicalSalesRepIdBetween(any(), any(), any()))
                .thenReturn(novemberPlans);
        lenient().when(healthCareProfValidator.existsAndActive(anyString())).thenReturn(true);
    }

    private static String newHcp() {
        return UUID.randomUUID().toString();
    }

    private void visited(String hcpId, LocalDate... dates) {
        for (LocalDate date : dates) {
            history.add(Visit.reconstruct(new VisitId(UUID.randomUUID().toString()),
                    new VisitDateTime(date.atTime(10, 0)), new HealthCareProfId(hcpId), null,
                    new Identifier(SITE_ID) {}, List.of(), new MedicalSalesRepId(MSR_ID), null));
        }
    }

    /** One past visit at which {@code items} were handed out, as alternating productId, quantity. */
    private void visitedWith(String hcpId, LocalDate date, Object... productAndQuantity) {
        List<VisitItem> items = new ArrayList<>();
        for (int i = 0; i < productAndQuantity.length; i += 2) {
            items.add(new VisitItem(new Identifier((String) productAndQuantity[i]) {}, (Integer) productAndQuantity[i + 1]));
        }
        history.add(Visit.reconstruct(new VisitId(UUID.randomUUID().toString()),
                new VisitDateTime(date.atTime(10, 0)), new HealthCareProfId(hcpId), null,
                new Identifier(SITE_ID) {}, items, new MedicalSalesRepId(MSR_ID), null));
    }

    private void planned(String hcpId, LocalDateTime at) throws Exception {
        novemberPlans.add(new VisitPlan(new VisitId(UUID.randomUUID().toString()), new VisitDateTime(at),
                new HealthCareProfId(hcpId), null, new Identifier(SITE_ID) {}, List.of(),
                new MedicalSalesRepId(MSR_ID)));
    }

    private VisitPlanSuggestionsOutputDTO suggestNovember() throws DomainException {
        return useCase.execute(new SuggestVisitPlansInputDTO(MSR_ID, NOVEMBER));
    }

    private static VisitPlanSuggestionDTO forHcp(VisitPlanSuggestionsOutputDTO out, String hcpId) {
        return out.suggestions().stream().filter(s -> s.healthCareProfId().equals(hcpId)).findFirst().orElseThrow();
    }

    @Nested
    class WhoIsDue {

        @Test
        void overdueHcpGoesFirstOnTheFirstWeekdayOfTheMonth() throws Exception {
            String hcp = newHcp();
            visited(hcp, LocalDate.of(2026, 9, 26), LocalDate.of(2026, 10, 10)); // every 14 days: due Oct 24

            VisitPlanSuggestionDTO s = forHcp(suggestNovember(), hcp);

            assertThat(s.visitDateTime()).isEqualTo(LocalDateTime.of(2026, 11, 2, 9, 0));
            assertThat(s.usualIntervalDays()).isEqualTo(14);
            assertThat(s.lastVisitDate()).isEqualTo(LocalDate.of(2026, 10, 10));
            assertThat(s.visitSiteId()).isEqualTo(SITE_ID);
            assertThat(s.reason()).contains("every 14 days").contains("overdue since 2026-10-24");
        }

        @Test
        void singleVisitUsesTheDefaultIntervalAndAWeekendDueDateMovesToMonday() throws Exception {
            String hcp = newHcp();
            visited(hcp, LocalDate.of(2026, 10, 15)); // + 30 days = Sat Nov 14

            VisitPlanSuggestionDTO s = forHcp(suggestNovember(), hcp);

            assertThat(s.visitDateTime()).isEqualTo(LocalDateTime.of(2026, 11, 16, 9, 0));
            assertThat(s.usualIntervalDays()).isEqualTo(SuggestVisitPlansUseCase.DEFAULT_INTERVAL_DAYS);
            assertThat(s.reason()).contains("only visit").contains("due 2026-11-14");
        }

        @Test
        void hcpNotDueUntilNextMonthIsLeftOut() throws Exception {
            String dueLastDay = newHcp();
            String dueInDecember = newHcp();
            visited(dueLastDay, LocalDate.of(2026, 8, 2), LocalDate.of(2026, 10, 1));     // 60 days: due Nov 30
            visited(dueInDecember, LocalDate.of(2026, 8, 11), LocalDate.of(2026, 10, 10)); // 60 days: due Dec 9

            VisitPlanSuggestionsOutputDTO out = suggestNovember();

            assertThat(forHcp(out, dueLastDay).visitDateTime()).isEqualTo(LocalDateTime.of(2026, 11, 30, 9, 0));
            assertThat(out.suggestions()).noneMatch(s -> s.healthCareProfId().equals(dueInDecember));
        }

        @Test
        void inactiveHcpIsSkipped() throws Exception {
            String hcp = newHcp();
            visited(hcp, LocalDate.of(2026, 10, 1));
            when(healthCareProfValidator.existsAndActive(hcp)).thenReturn(false);

            assertThat(suggestNovember().suggestions()).isEmpty();
        }

        @Test
        void hcpAlreadyPlannedThatMonthIsSkipped() throws Exception {
            String hcp = newHcp();
            visited(hcp, LocalDate.of(2026, 10, 1));
            planned(hcp, LocalDateTime.of(2026, 11, 20, 11, 0));

            assertThat(suggestNovember().suggestions()).isEmpty();
        }

        @Test
        void intervalIsClampedToAtLeastAWeek() throws Exception {
            String hcp = newHcp();
            visited(hcp, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3));

            assertThat(forHcp(suggestNovember(), hcp).usualIntervalDays())
                    .isEqualTo(SuggestVisitPlansUseCase.MIN_INTERVAL_DAYS);
        }
    }

    @Nested
    class Scheduling {

        @Test
        void slotsAvoidTheRepsExistingPlans() throws Exception {
            String hcp = newHcp();
            visited(hcp, LocalDate.of(2026, 10, 1));
            planned(newHcp(), LocalDateTime.of(2026, 11, 2, 9, 0));

            assertThat(forHcp(suggestNovember(), hcp).visitDateTime()).isEqualTo(LocalDateTime.of(2026, 11, 2, 10, 0));
        }

        @Test
        void aFullMonthReportsTheLeastOverdueAsUnscheduled() throws Exception {
            // November 2026 has 21 weekdays x 6 slots = 126 visits.
            List<String> hcps = new ArrayList<>();
            for (int i = 0; i < 130; i++) {
                String hcp = newHcp();
                hcps.add(hcp);
                visited(hcp, LocalDate.of(2026, 9, 1).plusDays(i % 30)); // due Oct 1..Oct 30: all overdue
            }

            VisitPlanSuggestionsOutputDTO out = suggestNovember();

            assertThat(out.suggestions()).hasSize(126);
            assertThat(out.unscheduledHealthCareProfIds()).hasSize(4);
            assertThat(out.suggestions()).allMatch(s -> s.visitDateTime().getDayOfWeek().getValue() <= 5);
            assertThat(out.suggestions()).extracting(VisitPlanSuggestionDTO::visitDateTime).doesNotHaveDuplicates();
        }
    }

    @Nested
    class Input {

        @Test
        void noMonthMeansNextMonth() throws Exception {
            assertThat(useCase.execute(new SuggestVisitPlansInputDTO(MSR_ID, null)).month()).isEqualTo(NOVEMBER);
        }

        @Test
        void currentMonthOnlyUsesDaysAfterToday() throws Exception {
            String hcp = newHcp();
            visited(hcp, LocalDate.of(2026, 9, 1));

            VisitPlanSuggestionsOutputDTO out = useCase.execute(new SuggestVisitPlansInputDTO(MSR_ID, YearMonth.of(2026, 10)));

            assertThat(forHcp(out, hcp).visitDateTime()).isEqualTo(LocalDateTime.of(2026, 10, 19, 9, 0));
        }

        @Test
        void pastMonthIsRefused() {
            assertThatThrownBy(() -> useCase.execute(new SuggestVisitPlansInputDTO(MSR_ID, YearMonth.of(2026, 9))))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("past month");
            verify(visitRepository, never()).findByMedicalSalesRepIdSince(any(), any());
        }

        @Test
        void missingMsrIsRefused() {
            assertThatThrownBy(() -> useCase.execute(new SuggestVisitPlansInputDTO(" ", NOVEMBER)))
                    .isInstanceOf(DomainException.class);
        }
    }

    @Nested
    class Products {

        private final String productA = UUID.randomUUID().toString();
        private final String productB = UUID.randomUUID().toString();
        private final String productC = UUID.randomUUID().toString();

        @Test
        void averagesTheLastThreeVisitsThatRecordedProducts() throws Exception {
            String hcp = newHcp();
            visitedWith(hcp, LocalDate.of(2026, 6, 1), productC, 50);                 // 4th most recent: ignored
            visitedWith(hcp, LocalDate.of(2026, 7, 1), productA, 4, productB, 1);
            visitedWith(hcp, LocalDate.of(2026, 8, 1), productA, 6);
            visitedWith(hcp, LocalDate.of(2026, 9, 1));                                // no products: skipped
            visitedWith(hcp, LocalDate.of(2026, 10, 1), productA, 5);

            List<SuggestedVisitItemDTO> items = forHcp(suggestNovember(), hcp).items();

            // A: (4 + 6 + 5) / 3 = 5. B: 1 / 3 rounds to 0, so it is left out. C: too old.
            assertThat(items).containsExactly(new SuggestedVisitItemDTO(productA, 5, true));
        }

        @Test
        void noRecordedProductsMeansNoItems() throws Exception {
            String hcp = newHcp();
            visited(hcp, LocalDate.of(2026, 10, 1));

            assertThat(forHcp(suggestNovember(), hcp).items()).isEmpty();
            verify(productAvailability, never()).isAvailable(anyString(), anyInt());
        }

        @Test
        void stockIsCheckedOncePerProductForTheMonthsTotal() throws Exception {
            String first = newHcp();
            String second = newHcp();
            visitedWith(first, LocalDate.of(2026, 10, 1), productA, 5);
            visitedWith(second, LocalDate.of(2026, 10, 2), productA, 7);
            when(productAvailability.isAvailable(productA, 12)).thenReturn(Optional.of(false));

            VisitPlanSuggestionsOutputDTO out = suggestNovember();

            assertThat(forHcp(out, first).items()).containsExactly(new SuggestedVisitItemDTO(productA, 5, false));
            assertThat(forHcp(out, second).items()).containsExactly(new SuggestedVisitItemDTO(productA, 7, false));
            verify(productAvailability, times(1)).isAvailable(productA, 12);
        }

        @Test
        void unreachableCatalogLeavesTheStockFlagUnknown() throws Exception {
            String hcp = newHcp();
            visitedWith(hcp, LocalDate.of(2026, 10, 1), productA, 3);
            when(productAvailability.isAvailable(productA, 3)).thenReturn(Optional.empty());

            assertThat(forHcp(suggestNovember(), hcp).items())
                    .containsExactly(new SuggestedVisitItemDTO(productA, 3, null));
        }

        @Test
        void mostUnitsComeFirst() throws Exception {
            String hcp = newHcp();
            visitedWith(hcp, LocalDate.of(2026, 10, 1), productA, 2, productB, 9);

            assertThat(forHcp(suggestNovember(), hcp).items())
                    .extracting(SuggestedVisitItemDTO::productId).containsExactly(productB, productA);
        }
    }
}
