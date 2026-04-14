package com.das.cleanddd.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.UseCaseOnlyOutput;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import com.das.cleanddd.domain.visit.usecases.dtos.CreateVisitPlanInputDTO;
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
    private final UseCaseOnlyOutput<List<VisitPlanOutputDTO>> listVisitPlansUseCase;

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
        log.info("POST /api/v1/visitplan/create - request received");
        try {
            VisitPlanOutputDTO result = createVisitPlanUseCase.execute(inputDTO);
            log.info("POST /api/v1/visitplan/create - success");
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (DomainException e) {
            log.warn("POST /api/v1/visitplan/create - domain error: {}", e.getMessage());
            throw e;
        }
    }

    @PutMapping("/update")
    @Operation(summary = "Update visit plan")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Object> updateVisitPlan(@Valid @RequestBody UpdateVisitPlanInputDTO inputDTO) throws DomainException {
        log.info("PUT /api/v1/visitplan/update - request received");
        try {
            VisitPlanOutputDTO result = updateVisitPlanUseCase.execute(inputDTO);
            log.info("PUT /api/v1/visitplan/update - success");
            return ResponseEntity.ok(result);
        } catch (DomainException e) {
            log.warn("PUT /api/v1/visitplan/update - domain error: {}", e.getMessage());
            throw e;
        }
    }

    @GetMapping("/get")
    @Operation(summary = "Get visit plan by ID")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Object> getVisitPlanById(@Valid @RequestBody VisitPlanIDDto inputDTO) throws DomainException {
        log.info("GET /api/v1/visitplan/get - request received");
        try {
            VisitPlanOutputDTO result = getVisitPlanByIdUseCase.execute(inputDTO);
            log.info("GET /api/v1/visitplan/get - success");
            return ResponseEntity.ok(result);
        } catch (DomainException e) {
            log.warn("GET /api/v1/visitplan/get - domain error: {}", e.getMessage());
            throw e;
        }
    }

    @PostMapping("/list")
    @Operation(summary = "List visit plans")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Object> listVisitPlans() throws DomainException {
        log.info("POST /api/v1/visitplan/list - request received");
        try {
            List<VisitPlanOutputDTO> result = listVisitPlansUseCase.execute();
            log.info("POST /api/v1/visitplan/list - success, count={}", result.size());
            return ResponseEntity.ok(result);
        } catch (DomainException e) {
            log.warn("POST /api/v1/visitplan/list - domain error: {}", e.getMessage());
            throw e;
        }
    }
}
