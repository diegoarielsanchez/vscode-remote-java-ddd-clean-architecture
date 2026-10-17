package com.das.cleanddd.domain.visit.usecases.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.das.cleanddd.domain.shared.Identifier;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import com.das.cleanddd.domain.visit.IVisitPlanRepository;
import com.das.cleanddd.domain.visit.entities.HealthCareProfId;
import com.das.cleanddd.domain.visit.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.visit.entities.VisitDateTime;
import com.das.cleanddd.domain.visit.entities.VisitId;
import com.das.cleanddd.domain.visit.entities.VisitItem;
import com.das.cleanddd.domain.visit.entities.VisitPlan;
import com.das.cleanddd.domain.visit.ports.IProductAvailability;
import com.das.cleanddd.domain.visit.usecases.dtos.DraftMonthlyOrdersInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.OrderDraftLineDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.OrderDraftOutputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitItemDTO;

/** "Today" is 2026-10-17 at noon, so the default month is November 2026. */
@ExtendWith(MockitoExtension.class)
class DraftMonthlyOrdersUseCaseTest {

    private static final Clock TODAY = Clock.fixed(
            LocalDateTime.of(2026, 10, 17, 12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);
    private static final String MSR_ID = "923e4567-e89b-12d3-a456-426614174000";

    @Mock private IVisitPlanRepository visitPlanRepository;
    @Mock private IProductAvailability productAvailability;

    private DraftMonthlyOrdersUseCase useCase;
    private final List<VisitPlan> plans = new ArrayList<>();
    private final String productA = UUID.randomUUID().toString();
    private final String productB = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        useCase = new DraftMonthlyOrdersUseCase(visitPlanRepository, productAvailability, TODAY);
        lenient().when(visitPlanRepository.findActiveByMedicalSalesRepIdBetween(any(), any(), any())).thenReturn(plans);
        lenient().when(productAvailability.isAvailable(anyString(), anyInt())).thenReturn(Optional.of(true));
    }

    /** A November plan bringing {@code items}, as alternating productId, quantity. */
    private void planned(Object... productAndQuantity) throws Exception {
        List<VisitItem> items = new ArrayList<>();
        for (int i = 0; i < productAndQuantity.length; i += 2) {
            items.add(new VisitItem(new Identifier((String) productAndQuantity[i]) {}, (Integer) productAndQuantity[i + 1]));
        }
        plans.add(new VisitPlan(new VisitId(UUID.randomUUID().toString()),
                new VisitDateTime(LocalDateTime.of(2026, 11, 10, 9, 0)),
                new HealthCareProfId(UUID.randomUUID().toString()), null,
                new Identifier(UUID.randomUUID().toString()) {}, items, new MedicalSalesRepId(MSR_ID)));
    }

    private OrderDraftOutputDTO draftNovember() throws DomainException {
        return useCase.execute(new DraftMonthlyOrdersInputDTO(MSR_ID, NOVEMBER));
    }

    @Test
    void addsUpEachProductAcrossTheMonthsPlans_mostUnitsFirst() throws Exception {
        planned(productA, 4, productB, 10);
        planned(productA, 3);
        planned(); // a plan without items still counts as a planned visit

        OrderDraftOutputDTO draft = draftNovember();

        assertThat(draft.plannedVisits()).isEqualTo(3);
        assertThat(draft.lines()).containsExactly(
                new OrderDraftLineDTO(productB, 10, 1, true),
                new OrderDraftLineDTO(productA, 7, 2, true));
        assertThat(draft.orderRequests()).singleElement()
                .satisfies(order -> assertThat(order.lines()).containsExactly(
                        new VisitItemDTO(productB, 10), new VisitItemDTO(productA, 7)));
        verify(productAvailability).isAvailable(productA, 7);
    }

    @Test
    void outOfStockProductsStayVisibleButAreLeftOutOfTheOrders() throws Exception {
        planned(productA, 4, productB, 2);
        when(productAvailability.isAvailable(productB, 2)).thenReturn(Optional.of(false));

        OrderDraftOutputDTO draft = draftNovember();

        assertThat(draft.lines()).extracting(OrderDraftLineDTO::productId).containsExactly(productA, productB);
        assertThat(draft.orderRequests()).singleElement()
                .satisfies(order -> assertThat(order.lines()).containsExactly(new VisitItemDTO(productA, 4)));
    }

    @Test
    void unknownStockIsStillOrdered() throws Exception {
        planned(productA, 4);
        when(productAvailability.isAvailable(productA, 4)).thenReturn(Optional.empty());

        OrderDraftOutputDTO draft = draftNovember();

        assertThat(draft.lines()).containsExactly(new OrderDraftLineDTO(productA, 4, 1, null));
        assertThat(draft.orderRequests()).singleElement()
                .satisfies(order -> assertThat(order.lines()).containsExactly(new VisitItemDTO(productA, 4)));
    }

    @Test
    void moreProductsThanOneOrderAllowsAreSplitAcrossOrders() throws Exception {
        Object[] items = new Object[2 * (DraftMonthlyOrdersUseCase.MAX_ORDER_LINES + 1)];
        for (int i = 0; i <= DraftMonthlyOrdersUseCase.MAX_ORDER_LINES; i++) {
            items[2 * i] = UUID.randomUUID().toString();
            items[2 * i + 1] = 1;
        }
        planned(items);

        OrderDraftOutputDTO draft = draftNovember();

        assertThat(draft.orderRequests()).hasSize(2);
        assertThat(draft.orderRequests().get(0).lines()).hasSize(DraftMonthlyOrdersUseCase.MAX_ORDER_LINES);
        assertThat(draft.orderRequests().get(1).lines()).hasSize(1);
    }

    @Test
    void noPlansMeansAnEmptyDraft() throws Exception {
        OrderDraftOutputDTO draft = draftNovember();

        assertThat(draft.plannedVisits()).isZero();
        assertThat(draft.lines()).isEmpty();
        assertThat(draft.orderRequests()).isEmpty();
    }

    @Test
    void noMonthMeansNextMonth_andTheWholeMonthIsCounted() throws Exception {
        assertThat(useCase.execute(new DraftMonthlyOrdersInputDTO(MSR_ID, null)).month()).isEqualTo(NOVEMBER);

        verify(visitPlanRepository).findActiveByMedicalSalesRepIdBetween(any(),
                eq(LocalDateTime.of(2026, 11, 1, 0, 0)), eq(LocalDateTime.of(2026, 12, 1, 0, 0)));
    }

    @Test
    void currentMonthOnlyCountsPlansStillAhead() throws Exception {
        useCase.execute(new DraftMonthlyOrdersInputDTO(MSR_ID, YearMonth.of(2026, 10)));

        verify(visitPlanRepository).findActiveByMedicalSalesRepIdBetween(any(),
                eq(LocalDateTime.of(2026, 10, 17, 12, 0)), eq(LocalDateTime.of(2026, 11, 1, 0, 0)));
    }

    @Test
    void pastMonthIsRefused() {
        assertThatThrownBy(() -> useCase.execute(new DraftMonthlyOrdersInputDTO(MSR_ID, YearMonth.of(2026, 9))))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("past month");
    }
}
