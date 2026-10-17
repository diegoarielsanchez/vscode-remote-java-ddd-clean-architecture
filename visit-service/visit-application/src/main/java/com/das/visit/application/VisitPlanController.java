package com.das.visit.application;

import java.time.YearMonth;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import com.das.cleanddd.domain.visit.usecases.dtos.CreateVisitPlanInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.DraftMonthlyOrdersInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.ListVisitPlansInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.OrderDraftOutputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.SuggestVisitPlansInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.UpdateVisitPlanInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitPlanSuggestionsOutputDTO;
import com.das.visit.application.security.CallerMsr;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitPlanIDDto;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitPlanOutputDTO;
import com.das.cleanddd.domain.visit.usecases.services.VisitPlanUseCaseFactory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@CrossOrigin(exposedHeaders = "errors, content-type")
@RequestMapping("api/v1/visitplan")
@Tag(name = "VisitPlan", description = "API for managing Visit Plans")
@SecurityRequirement(name = "bearerAuth")
public class VisitPlanController {

    private static final Logger log = LoggerFactory.getLogger(VisitPlanController.class);

    @Autowired
    private final UseCase<CreateVisitPlanInputDTO, VisitPlanOutputDTO> createVisitPlanUseCase;
    private final UseCase<UpdateVisitPlanInputDTO, VisitPlanOutputDTO> updateVisitPlanUseCase;
    private final UseCase<VisitPlanIDDto, VisitPlanOutputDTO> getVisitPlanByIdUseCase;
    private final UseCase<ListVisitPlansInputDTO, List<VisitPlanOutputDTO>> listVisitPlansUseCase;
    private final UseCase<SuggestVisitPlansInputDTO, VisitPlanSuggestionsOutputDTO> suggestVisitPlansUseCase;
    private final UseCase<DraftMonthlyOrdersInputDTO, OrderDraftOutputDTO> draftMonthlyOrdersUseCase;

    public VisitPlanController(VisitPlanUseCaseFactory visitPlanUseCaseFactory) {
        this.createVisitPlanUseCase = visitPlanUseCaseFactory.getCreateVisitPlanUseCase();
        this.updateVisitPlanUseCase = visitPlanUseCaseFactory.getUpdateVisitPlanUseCase();
        this.getVisitPlanByIdUseCase = visitPlanUseCaseFactory.getVisitPlanByIdUseCase();
        this.listVisitPlansUseCase = visitPlanUseCaseFactory.getListVisitPlansUseCase();
        this.suggestVisitPlansUseCase = visitPlanUseCaseFactory.getSuggestVisitPlansUseCase();
        this.draftMonthlyOrdersUseCase = visitPlanUseCaseFactory.getDraftMonthlyOrdersUseCase();
    }

    @PostMapping("/create")
    @Operation(summary = "Create visit plan")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Object> createVisitPlan(@Valid @RequestBody CreateVisitPlanInputDTO inputDTO,
                                                  Authentication authentication) throws DomainException {
        log.info("POST /api/v1/visitplan/create");
        String msrId = CallerMsr.require(authentication, inputDTO.medicalSalesRepId(), "visit plans");
        return ResponseEntity.status(HttpStatus.CREATED).body(createVisitPlanUseCase.execute(new CreateVisitPlanInputDTO(
                inputDTO.visitDateTime(), inputDTO.healthCareProfId(), inputDTO.visitComments(),
                inputDTO.visitSiteId(), msrId, inputDTO.items())));
    }

    @PutMapping("/update")
    @Operation(summary = "Update visit plan")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Object> updateVisitPlan(@Valid @RequestBody UpdateVisitPlanInputDTO inputDTO,
                                                  Authentication authentication) throws DomainException {
        log.info("PUT /api/v1/visitplan/update");
        // The use case also refuses a plan that belongs to another MSR.
        String msrId = CallerMsr.require(authentication, inputDTO.medicalSalesRepId(), "visit plans");
        return ResponseEntity.ok(updateVisitPlanUseCase.execute(new UpdateVisitPlanInputDTO(
                inputDTO.id(), inputDTO.visitDateTime(), inputDTO.healthCareProfId(), inputDTO.visitComments(),
                inputDTO.visitSiteId(), msrId, inputDTO.items())));
    }

    /**
     * Read-only draft of a month of visit plans, built from the MSR's visit history. Nothing is saved.
     * A rep always gets their own; the AI Agent Service must name the MSR it is planning for.
     */
    @GetMapping("/suggestions")
    @Operation(summary = "Suggest visit plans for a month (default: next month) — read-only, nothing is saved")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<VisitPlanSuggestionsOutputDTO> suggestVisitPlans(
            @RequestParam(required = false) YearMonth month,
            @RequestParam(required = false) String medicalSalesRepId,
            Authentication authentication) throws DomainException {
        log.info("GET /api/v1/visitplan/suggestions month={}", sanitize(String.valueOf(month)));
        String msrId = plannedMsrId(authentication, medicalSalesRepId);
        return ResponseEntity.ok(suggestVisitPlansUseCase.execute(new SuggestVisitPlansInputDTO(msrId, month)));
    }

    /**
     * Read-only draft of the month's product order: the items of the MSR's active visit plans that month,
     * added up per product, plus ready-to-submit bodies for {@code POST /api/v1/orders/create}. Nothing is
     * ordered. Same caller rules as suggestions.
     */
    @GetMapping("/order-draft")
    @Operation(summary = "Draft the month's product order from active visit plans (default: next month) — read-only")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<OrderDraftOutputDTO> draftMonthlyOrders(
            @RequestParam(required = false) YearMonth month,
            @RequestParam(required = false) String medicalSalesRepId,
            Authentication authentication) throws DomainException {
        log.info("GET /api/v1/visitplan/order-draft month={}", sanitize(String.valueOf(month)));
        String msrId = plannedMsrId(authentication, medicalSalesRepId);
        return ResponseEntity.ok(draftMonthlyOrdersUseCase.execute(new DraftMonthlyOrdersInputDTO(msrId, month)));
    }

    /**
     * The MSR a read-only draft is for: a rep always gets their own; the AI Agent Service, which has no
     * MSR of its own, must name one.
     */
    private static String plannedMsrId(Authentication authentication, String medicalSalesRepId) {
        boolean isAgent = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_AGENT".equals(authority.getAuthority()));
        if (isAgent && medicalSalesRepId != null && !medicalSalesRepId.isBlank()) {
            return medicalSalesRepId;
        }
        if (isAgent && CallerMsr.of(authentication) == null) {
            throw new InvalidInputException("medicalSalesRepId is required when the AI Agent Service asks for a draft");
        }
        return CallerMsr.require(authentication, medicalSalesRepId, "visit plans");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get visit plan by ID")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Object> getVisitPlanById(@PathVariable String id) throws DomainException {
        log.info("GET /api/v1/visitplan/{}", sanitize(id));
        return ResponseEntity.ok(getVisitPlanByIdUseCase.execute(new VisitPlanIDDto(id)));
    }

    @PostMapping("/list")
    @Operation(summary = "List visit plans")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Object> listVisitPlans(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "10") int pageSize) throws DomainException {
        log.info("POST /api/v1/visitplan/list");
        pageSize = Math.min(pageSize, 100);
        return ResponseEntity.ok(listVisitPlansUseCase.execute(new ListVisitPlansInputDTO(page, pageSize)));
    }

    /** Strips log-injection characters from user-supplied values (OWASP A09). */
    private static String sanitize(String value) {
        return value == null ? "" : value.replaceAll("[\r\n\t]", "_");
    }
}
