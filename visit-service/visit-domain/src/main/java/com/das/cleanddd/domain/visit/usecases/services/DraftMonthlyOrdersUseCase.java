package com.das.cleanddd.domain.visit.usecases.services;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import com.das.cleanddd.domain.visit.IVisitPlanRepository;
import com.das.cleanddd.domain.visit.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.visit.entities.VisitItem;
import com.das.cleanddd.domain.visit.entities.VisitPlan;
import com.das.cleanddd.domain.visit.ports.IProductAvailability;
import com.das.cleanddd.domain.visit.usecases.dtos.DraftMonthlyOrdersInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.OrderDraftLineDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.OrderDraftOutputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.OrderRequestDraftDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitItemDTO;

/**
 * Drafts the product order an MSR needs for a month: the items of their active visit plans in that
 * month, added up per product. Read-only — the rep (or the AI Agent Service proposing to them)
 * submits the drafted bodies to order-service, which places orders for the caller's own MSR.
 * <p>
 * Only the plans' items count: the rep's samples on hand are not tracked, so nothing is subtracted.
 * In the current month only plans still ahead are counted.
 */
public final class DraftMonthlyOrdersUseCase implements UseCase<DraftMonthlyOrdersInputDTO, OrderDraftOutputDTO> {

    /** Mirrors order-service's limit on lines per order. */
    static final int MAX_ORDER_LINES = 20;

    private final IVisitPlanRepository visitPlanRepository;
    private final IProductAvailability productAvailability;
    private final Clock clock;

    public DraftMonthlyOrdersUseCase(IVisitPlanRepository visitPlanRepository,
                                     IProductAvailability productAvailability,
                                     Clock clock) {
        this.visitPlanRepository = visitPlanRepository;
        this.productAvailability = productAvailability;
        this.clock = clock;
    }

    @Override
    public OrderDraftOutputDTO execute(DraftMonthlyOrdersInputDTO input) throws DomainException {
        if (input == null || input.medicalSalesRepId() == null || input.medicalSalesRepId().isBlank()) {
            throw new DomainException("Medical Sales Representative id cannot be null or empty");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        YearMonth month = input.month() != null ? input.month() : YearMonth.from(now).plusMonths(1);
        if (month.isBefore(YearMonth.from(now))) {
            throw new DomainException("Cannot draft orders for a past month.");
        }
        MedicalSalesRepId msrId;
        try {
            msrId = new MedicalSalesRepId(input.medicalSalesRepId());
        } catch (IllegalArgumentException e) {
            throw new DomainException(e.getMessage());
        }

        LocalDateTime monthStart = month.atDay(1).atStartOfDay();
        LocalDateTime from = monthStart.isAfter(now) ? monthStart : now;
        LocalDateTime toExclusive = month.atEndOfMonth().plusDays(1).atStartOfDay();
        List<VisitPlan> plans = visitPlanRepository.findActiveByMedicalSalesRepIdBetween(msrId, from, toExclusive);

        Map<String, Integer> quantityByProduct = new LinkedHashMap<>();
        Map<String, Integer> plansByProduct = new LinkedHashMap<>();
        for (VisitPlan plan : plans) {
            for (VisitItem item : plan.visitItems()) {
                String productId = item.productId().value();
                quantityByProduct.merge(productId, item.quantity(), Integer::sum);
                plansByProduct.merge(productId, 1, Integer::sum);
            }
        }

        List<OrderDraftLineDTO> lines = new ArrayList<>();
        quantityByProduct.forEach((productId, quantity) -> lines.add(new OrderDraftLineDTO(productId, quantity,
                plansByProduct.get(productId), productAvailability.isAvailable(productId, quantity).orElse(null))));
        lines.sort(Comparator.comparingInt(OrderDraftLineDTO::quantity).reversed()
                .thenComparing(OrderDraftLineDTO::productId));

        return new OrderDraftOutputDTO(msrId.value(), month, plans.size(), lines, orderRequests(lines));
    }

    /** The lines not known to be short, in orders of at most {@value #MAX_ORDER_LINES} lines. */
    private static List<OrderRequestDraftDTO> orderRequests(List<OrderDraftLineDTO> lines) {
        List<VisitItemDTO> orderable = lines.stream()
                .filter(line -> !Boolean.FALSE.equals(line.inStock()))
                .map(line -> new VisitItemDTO(line.productId(), line.quantity()))
                .toList();
        List<OrderRequestDraftDTO> requests = new ArrayList<>();
        for (int i = 0; i < orderable.size(); i += MAX_ORDER_LINES) {
            requests.add(new OrderRequestDraftDTO(orderable.subList(i, Math.min(i + MAX_ORDER_LINES, orderable.size()))));
        }
        return requests;
    }
}
