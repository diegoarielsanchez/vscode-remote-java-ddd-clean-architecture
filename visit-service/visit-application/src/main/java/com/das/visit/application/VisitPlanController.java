package com.das.visit.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
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
import com.das.cleanddd.domain.visit.usecases.dtos.ListVisitPlansInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.UpdateVisitPlanInputDTO;
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

    public VisitPlanController(VisitPlanUseCaseFactory visitPlanUseCaseFactory) {
        this.createVisitPlanUseCase = visitPlanUseCaseFactory.getCreateVisitPlanUseCase();
        this.updateVisitPlanUseCase = visitPlanUseCaseFactory.getUpdateVisitPlanUseCase();
        this.getVisitPlanByIdUseCase = visitPlanUseCaseFactory.getVisitPlanByIdUseCase();
        this.listVisitPlansUseCase = visitPlanUseCaseFactory.getListVisitPlansUseCase();
    }

    @PostMapping("/create")
    @Operation(summary = "Create visit plan")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Object> createVisitPlan(@Valid @RequestBody CreateVisitPlanInputDTO inputDTO) throws DomainException {
        log.info("POST /api/v1/visitplan/create");
        return ResponseEntity.status(HttpStatus.CREATED).body(createVisitPlanUseCase.execute(inputDTO));
    }

    @PutMapping("/update")
    @Operation(summary = "Update visit plan")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Object> updateVisitPlan(@Valid @RequestBody UpdateVisitPlanInputDTO inputDTO) throws DomainException {
        log.info("PUT /api/v1/visitplan/update");
        return ResponseEntity.ok(updateVisitPlanUseCase.execute(inputDTO));
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
