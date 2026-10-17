package com.das.cleanddd.domain.visit.usecases.services;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import com.das.cleanddd.domain.visit.IVisitPlanRepository;
import com.das.cleanddd.domain.visit.IVisitRepository;
import com.das.cleanddd.domain.visit.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.visit.entities.Visit;
import com.das.cleanddd.domain.visit.entities.VisitItem;
import com.das.cleanddd.domain.visit.entities.VisitPlan;
import com.das.cleanddd.domain.visit.ports.IHealthCareProfValidator;
import com.das.cleanddd.domain.visit.ports.IProductAvailability;
import com.das.cleanddd.domain.visit.usecases.dtos.SuggestVisitPlansInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.SuggestedVisitItemDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitPlanSuggestionDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitPlanSuggestionsOutputDTO;

/**
 * Drafts a month of visit plans for one Medical Sales Rep from their own visit history.
 * Read-only: nothing is saved — the rep (or the AI Agent Service on their behalf) reviews the
 * suggestions and creates the plans they accept.
 * <p>
 * For each active HCP the rep visited in the last 12 months: the usual gap between visits gives
 * the next due date; HCPs due by the end of the month are suggested, most overdue first, at the
 * site of their last visit. Visits go on weekdays, in hourly slots from 09:00, at most
 * {@value #MAX_VISITS_PER_DAY} a day, around the rep's existing plans. HCPs already planned that
 * month are skipped.
 * <p>
 * Each visit lists the products to bring: per product, the average quantity per visit over the
 * HCP's last {@value #ITEM_LOOKBACK_VISITS} visits that recorded products (rounded; products that
 * average under one unit are left out). Each product's total for the month is then checked once
 * against the catalog and flagged {@code inStock}.
 */
public final class SuggestVisitPlansUseCase
        implements UseCase<SuggestVisitPlansInputDTO, VisitPlanSuggestionsOutputDTO> {

    static final int LOOKBACK_MONTHS = 12;
    static final int DEFAULT_INTERVAL_DAYS = 30;
    static final int MIN_INTERVAL_DAYS = 7;
    static final int MAX_INTERVAL_DAYS = 90;
    static final int MAX_VISITS_PER_DAY = 6;
    static final LocalTime FIRST_SLOT = LocalTime.of(9, 0);
    static final int ITEM_LOOKBACK_VISITS = 3;

    private final IVisitRepository visitRepository;
    private final IVisitPlanRepository visitPlanRepository;
    private final IHealthCareProfValidator healthCareProfValidator;
    private final IProductAvailability productAvailability;
    private final Clock clock;

    public SuggestVisitPlansUseCase(IVisitRepository visitRepository,
                                    IVisitPlanRepository visitPlanRepository,
                                    IHealthCareProfValidator healthCareProfValidator,
                                    IProductAvailability productAvailability,
                                    Clock clock) {
        this.visitRepository = visitRepository;
        this.visitPlanRepository = visitPlanRepository;
        this.healthCareProfValidator = healthCareProfValidator;
        this.productAvailability = productAvailability;
        this.clock = clock;
    }

    @Override
    public VisitPlanSuggestionsOutputDTO execute(SuggestVisitPlansInputDTO input) throws DomainException {
        if (input == null || input.medicalSalesRepId() == null || input.medicalSalesRepId().isBlank()) {
            throw new DomainException("Medical Sales Representative id cannot be null or empty");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        YearMonth month = input.month() != null ? input.month() : YearMonth.from(now).plusMonths(1);
        if (month.isBefore(YearMonth.from(now))) {
            throw new DomainException("Cannot suggest visit plans for a past month.");
        }
        MedicalSalesRepId msrId;
        try {
            msrId = new MedicalSalesRepId(input.medicalSalesRepId());
        } catch (IllegalArgumentException e) {
            throw new DomainException(e.getMessage());
        }

        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();
        // Never propose today or earlier: plans must be in the future.
        LocalDate firstPlannableDay = monthStart.isAfter(now.toLocalDate()) ? monthStart : now.toLocalDate().plusDays(1);

        List<VisitPlan> existingPlans = visitPlanRepository.findActiveByMedicalSalesRepIdBetween(
                msrId, monthStart.atStartOfDay(), monthEnd.plusDays(1).atStartOfDay());
        Set<String> alreadyPlannedHcps = existingPlans.stream()
                .map(plan -> plan.healthCareProfId().value())
                .collect(Collectors.toSet());

        List<Candidate> candidates = dueCandidates(msrId, now, monthEnd, alreadyPlannedHcps);

        Calendar calendar = new Calendar(existingPlans);
        Map<Candidate, LocalDateTime> scheduled = new LinkedHashMap<>();
        List<String> unscheduled = new ArrayList<>();
        for (Candidate candidate : candidates) {
            LocalDate earliest = candidate.dueDate().isAfter(firstPlannableDay) ? candidate.dueDate() : firstPlannableDay;
            LocalDateTime slot = calendar.book(earliest, monthEnd);
            if (slot == null) {
                unscheduled.add(candidate.healthCareProfId());
            } else {
                scheduled.put(candidate, slot);
            }
        }

        Map<String, Boolean> inStock = stockForMonthTotals(scheduled.keySet());
        List<VisitPlanSuggestionDTO> suggestions = new ArrayList<>();
        scheduled.forEach((candidate, slot) -> suggestions.add(new VisitPlanSuggestionDTO(
                candidate.healthCareProfId(), slot, candidate.visitSiteId(), candidate.lastVisitDate(),
                candidate.intervalDays(), candidate.reason(monthStart),
                candidate.items().stream()
                        .map(line -> new SuggestedVisitItemDTO(line.productId(), line.quantity(),
                                inStock.get(line.productId())))
                        .toList())));
        suggestions.sort(Comparator.comparing(VisitPlanSuggestionDTO::visitDateTime));
        return new VisitPlanSuggestionsOutputDTO(msrId.value(), month, suggestions, unscheduled);
    }

    /** Active, not-yet-planned HCPs due by {@code monthEnd}, most overdue first. */
    private List<Candidate> dueCandidates(MedicalSalesRepId msrId, LocalDateTime now, LocalDate monthEnd,
                                          Set<String> alreadyPlannedHcps) {
        Map<String, List<Visit>> pastVisitsByHcp = visitRepository
                .findByMedicalSalesRepIdSince(msrId, now.minusMonths(LOOKBACK_MONTHS)).stream()
                .filter(visit -> !visit.visitDate().value().isAfter(now))
                .collect(Collectors.groupingBy(visit -> visit.healthCareProfId().value()));

        List<Candidate> candidates = new ArrayList<>();
        pastVisitsByHcp.forEach((hcpId, visits) -> {
            if (alreadyPlannedHcps.contains(hcpId) || !healthCareProfValidator.existsAndActive(hcpId)) {
                return;
            }
            visits.sort(Comparator.comparing(visit -> visit.visitDate().value()));
            Visit last = visits.get(visits.size() - 1);
            int interval = usualIntervalDays(visits);
            LocalDate lastDate = last.visitDate().value().toLocalDate();
            LocalDate due = lastDate.plusDays(interval);
            // A plan needs a site; without one on file the rep has to plan this HCP by hand.
            if (!due.isAfter(monthEnd) && last.visitSideId() != null) {
                candidates.add(new Candidate(hcpId, last.visitSideId().value(), lastDate, visits.size(), interval, due,
                        usualItems(visits)));
            }
        });
        candidates.sort(Comparator.comparing(Candidate::dueDate).thenComparing(Candidate::healthCareProfId));
        return candidates;
    }

    /** Mean gap between consecutive visits, kept within sensible bounds; a default for a single visit. */
    static int usualIntervalDays(List<Visit> visitsOldestFirst) {
        if (visitsOldestFirst.size() < 2) {
            return DEFAULT_INTERVAL_DAYS;
        }
        LocalDate first = visitsOldestFirst.get(0).visitDate().value().toLocalDate();
        LocalDate last = visitsOldestFirst.get(visitsOldestFirst.size() - 1).visitDate().value().toLocalDate();
        long mean = Math.round((double) ChronoUnit.DAYS.between(first, last) / (visitsOldestFirst.size() - 1));
        return (int) Math.max(MIN_INTERVAL_DAYS, Math.min(MAX_INTERVAL_DAYS, mean));
    }

    /**
     * Per product, the average quantity per visit over the last {@value #ITEM_LOOKBACK_VISITS} visits
     * that recorded products, most units first. Products averaging under one unit are left out.
     */
    static List<ProductQuantity> usualItems(List<Visit> visitsOldestFirst) {
        List<Visit> recentWithItems = new ArrayList<>();
        for (int i = visitsOldestFirst.size() - 1; i >= 0 && recentWithItems.size() < ITEM_LOOKBACK_VISITS; i--) {
            if (!visitsOldestFirst.get(i).visitItems().isEmpty()) {
                recentWithItems.add(visitsOldestFirst.get(i));
            }
        }
        if (recentWithItems.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (Visit visit : recentWithItems) {
            for (VisitItem item : visit.visitItems()) {
                totals.merge(item.productId().value(), item.quantity(), Integer::sum);
            }
        }
        List<ProductQuantity> usual = new ArrayList<>();
        totals.forEach((productId, total) -> {
            long average = Math.round((double) total / recentWithItems.size());
            if (average >= 1) {
                usual.add(new ProductQuantity(productId, (int) Math.min(average, VisitItem.MAX_QUANTITY)));
            }
        });
        usual.sort(Comparator.comparingInt(ProductQuantity::quantity).reversed()
                .thenComparing(ProductQuantity::productId));
        return usual;
    }

    /** One catalog check per product, for its total across the month's scheduled suggestions. */
    private Map<String, Boolean> stockForMonthTotals(Iterable<Candidate> scheduled) {
        Map<String, Integer> monthTotals = new LinkedHashMap<>();
        for (Candidate candidate : scheduled) {
            for (ProductQuantity line : candidate.items()) {
                monthTotals.merge(line.productId(), line.quantity(), Integer::sum);
            }
        }
        Map<String, Boolean> inStock = new HashMap<>();
        monthTotals.forEach((productId, total) ->
                inStock.put(productId, productAvailability.isAvailable(productId, total).orElse(null)));
        return inStock;
    }

    record ProductQuantity(String productId, int quantity) {}

    private record Candidate(String healthCareProfId, String visitSiteId, LocalDate lastVisitDate,
                             int visitCount, int intervalDays, LocalDate dueDate, List<ProductQuantity> items) {

        String reason(LocalDate monthStart) {
            String cadence = visitCount < 2
                    ? "only visit in the last " + LOOKBACK_MONTHS + " months; default follow-up after "
                            + DEFAULT_INTERVAL_DAYS + " days"
                    : "usually visited every " + intervalDays + " days";
            String due = dueDate.isBefore(monthStart) ? "overdue since " + dueDate : "due " + dueDate;
            return "Last visited " + lastVisitDate + " (" + cadence + "), " + due + ".";
        }
    }

    /** Free weekday slots in the month, around the rep's existing plans. */
    private static final class Calendar {

        private final Map<LocalDate, Set<LocalTime>> taken = new HashMap<>();

        Calendar(List<VisitPlan> existingPlans) {
            for (VisitPlan plan : existingPlans) {
                LocalDateTime at = plan.visitTimeDate().value();
                taken.computeIfAbsent(at.toLocalDate(), day -> new HashSet<>()).add(at.toLocalTime());
            }
        }

        /** Books the first free slot on or after {@code earliest}; {@code null} when the month is full. */
        LocalDateTime book(LocalDate earliest, LocalDate monthEnd) {
            for (LocalDate day = earliest; !day.isAfter(monthEnd); day = day.plusDays(1)) {
                if (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
                    continue;
                }
                Set<LocalTime> busy = taken.computeIfAbsent(day, d -> new HashSet<>());
                if (busy.size() >= MAX_VISITS_PER_DAY) {
                    continue;
                }
                for (int i = 0; i < MAX_VISITS_PER_DAY; i++) {
                    LocalTime slot = FIRST_SLOT.plusHours(i);
                    if (busy.add(slot)) {
                        return day.atTime(slot);
                    }
                }
            }
            return null;
        }
    }
}
