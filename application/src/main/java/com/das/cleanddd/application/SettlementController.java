package com.das.cleanddd.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import com.das.cleanddd.domain.settlement.usecases.dtos.CreateSettlementInputDTO;
import com.das.cleanddd.domain.settlement.usecases.dtos.SettlementIDDto;
import com.das.cleanddd.domain.settlement.usecases.dtos.SettlementOutputDTO;
import com.das.cleanddd.domain.settlement.usecases.dtos.UpdateSettlementInputDTO;
import com.das.cleanddd.domain.settlement.usecases.services.SettlementUseCaseFactory;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.UseCaseOnlyOutput;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@CrossOrigin(exposedHeaders = "errors, content-type")
@RequestMapping("api/v1/settlement")
@Tag(name = "Settlement", description = "API for managing Settlements")
@SecurityRequirement(name = "bearerAuth")
public class SettlementController {

    private static final Logger log = LoggerFactory.getLogger(SettlementController.class);

    @Autowired
    private final UseCase<CreateSettlementInputDTO, SettlementOutputDTO> createSettlementUseCase;
    private final UseCase<UpdateSettlementInputDTO, SettlementOutputDTO> updateSettlementUseCase;
    private final UseCase<SettlementIDDto, SettlementOutputDTO> getSettlementByIdUseCase;
    private final UseCaseOnlyOutput<List<SettlementOutputDTO>> listSettlementsUseCase;

    public SettlementController(SettlementUseCaseFactory settlementUseCaseFactory) {
        this.createSettlementUseCase = settlementUseCaseFactory.getCreateSettlementUseCase();
        this.updateSettlementUseCase = settlementUseCaseFactory.getUpdateSettlementUseCase();
        this.getSettlementByIdUseCase = settlementUseCaseFactory.getSettlementByIdUseCase();
        this.listSettlementsUseCase = settlementUseCaseFactory.getListSettlementsUseCase();
    }

    @PostMapping("/create")
    @Operation(summary = "Create settlement")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Object> createSettlement(@Valid @RequestBody CreateSettlementInputDTO inputDTO) throws DomainException {
        log.info("POST /api/v1/settlement/create - request received");
        try {
            SettlementOutputDTO result = createSettlementUseCase.execute(inputDTO);
            log.info("POST /api/v1/settlement/create - success");
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (DomainException e) {
            log.warn("POST /api/v1/settlement/create - domain error: {}", e.getMessage());
            throw e;
        }
    }

    @PutMapping("/update")
    @Operation(summary = "Update settlement")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Object> updateSettlement(@Valid @RequestBody UpdateSettlementInputDTO inputDTO) throws DomainException {
        log.info("PUT /api/v1/settlement/update - request received");
        try {
            SettlementOutputDTO result = updateSettlementUseCase.execute(inputDTO);
            log.info("PUT /api/v1/settlement/update - success");
            return ResponseEntity.ok(result);
        } catch (DomainException e) {
            log.warn("PUT /api/v1/settlement/update - domain error: {}", e.getMessage());
            throw e;
        }
    }

    @GetMapping("/get")
    @Operation(summary = "Get settlement by ID")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Object> getSettlementById(@Valid @RequestBody SettlementIDDto inputDTO) throws DomainException {
        log.info("GET /api/v1/settlement/get - request received");
        try {
            SettlementOutputDTO result = getSettlementByIdUseCase.execute(inputDTO);
            log.info("GET /api/v1/settlement/get - success");
            return ResponseEntity.ok(result);
        } catch (DomainException e) {
            log.warn("GET /api/v1/settlement/get - domain error: {}", e.getMessage());
            throw e;
        }
    }

    @PostMapping("/list")
    @Operation(summary = "List settlements")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Object> listSettlements() throws DomainException {
        log.info("POST /api/v1/settlement/list - request received");
        try {
            List<SettlementOutputDTO> result = listSettlementsUseCase.execute();
            log.info("POST /api/v1/settlement/list - success, count={}", result.size());
            return ResponseEntity.ok(result);
        } catch (DomainException e) {
            log.warn("POST /api/v1/settlement/list - domain error: {}", e.getMessage());
            throw e;
        }
    }
}
