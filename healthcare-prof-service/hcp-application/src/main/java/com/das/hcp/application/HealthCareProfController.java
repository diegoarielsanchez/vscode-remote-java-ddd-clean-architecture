package com.das.hcp.application;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.das.cleanddd.domain.healthcareprof.entities.Specialty;
import com.das.cleanddd.domain.healthcareprof.entities.SpecialtyCatalog;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.CreateHealthCareProfInputDTO;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.HealthCareProfIDDto;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.HealthCareProfNamesInputDTO;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.HealthCareProfOutputDTO;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.UpdateHealthCareProfInputDTO;
import com.das.cleanddd.domain.healthcareprof.usecases.services.HealthCareProfUseCaseFactory;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.UseCaseOnlyInput;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@CrossOrigin(exposedHeaders = "errors, content-type")
@RequestMapping("api/v1/healthcareprof")
@Tag(name = "Health Care Professional", description = "API for managing Health Care Professionals")
@SecurityRequirement(name = "bearerAuth")
public class HealthCareProfController {

    @Autowired
    private final UseCase<CreateHealthCareProfInputDTO, HealthCareProfOutputDTO> createHealthCareProfUseCase;
    private final UseCase<UpdateHealthCareProfInputDTO, HealthCareProfOutputDTO> updateHealthCareProfUseCase;
    private final UseCaseOnlyInput<HealthCareProfIDDto> activateHealthCareProfUseCase;
    private final UseCaseOnlyInput<HealthCareProfIDDto> deactivateHealthCareProfUseCase;
    private final UseCase<HealthCareProfIDDto, HealthCareProfOutputDTO> getGetHealthCareProfByIdUseCase;
    private final UseCase<HealthCareProfNamesInputDTO, List<HealthCareProfOutputDTO>> findHealthCareProfByNameUseCase;

    public HealthCareProfController(HealthCareProfUseCaseFactory factory) {
        this.createHealthCareProfUseCase = factory.getCreateHealthCareProfUseCase();
        this.updateHealthCareProfUseCase = factory.getUpdateHealthCareProfUseCase();
        this.activateHealthCareProfUseCase = factory.getActivateHealthCareProfUseCase();
        this.deactivateHealthCareProfUseCase = factory.getDeactivateHealthCareProfUseCase();
        this.getGetHealthCareProfByIdUseCase = factory.getGetHealthCareProfByIdUseCase();
        this.findHealthCareProfByNameUseCase = factory.getFindHealthCareProfByNameUseCase();
    }

    @PostMapping("/create")
    @Operation(summary = "Create health care professional")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Object> createHealthCareProf(@Valid @RequestBody CreateHealthCareProfInputDTO inputDTO) throws DomainException {
        return ResponseEntity.status(HttpStatus.CREATED).body(createHealthCareProfUseCase.execute(inputDTO));
    }

    @PutMapping("/update")
    @Operation(summary = "Update health care professional")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Object> updateHealthCareProf(@Valid @RequestBody UpdateHealthCareProfInputDTO inputDTO) throws DomainException {
        return ResponseEntity.ok(updateHealthCareProfUseCase.execute(inputDTO));
    }

    @PostMapping("/activate")
    @Operation(summary = "Activate health care professional")
    @ResponseStatus(HttpStatus.OK)
    public void activateHealthCareProf(@Valid @RequestBody HealthCareProfIDDto inputDTO) throws DomainException {
        activateHealthCareProfUseCase.execute(inputDTO);
    }

    @PostMapping("/deactivate")
    @Operation(summary = "Deactivate health care professional")
    @ResponseStatus(HttpStatus.OK)
    public void deactivateHealthCareProf(@Valid @RequestBody HealthCareProfIDDto inputDTO) throws DomainException {
        deactivateHealthCareProfUseCase.execute(inputDTO);
    }

    @GetMapping("/get")
    @Operation(summary = "Get health care professional by ID")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Object> getHealthCareProfByID(@Valid @RequestBody HealthCareProfIDDto inputDTO) throws DomainException {
        return ResponseEntity.ok(getGetHealthCareProfByIdUseCase.execute(inputDTO));
    }

    @PostMapping("/list")
    @Operation(summary = "List health care professionals by name")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Object> findHealthCareProfByName(
        @RequestParam(required = false, defaultValue = "") String firstName,
        @RequestParam(required = false, defaultValue = "") String lastName,
        @RequestParam(required = false, defaultValue = "1") int page,
        @RequestParam(required = false, defaultValue = "10") int pageSize
    ) throws DomainException {
        HealthCareProfNamesInputDTO inputDTO = new HealthCareProfNamesInputDTO(firstName, lastName, page, pageSize);
        return ResponseEntity.ok(findHealthCareProfByNameUseCase.execute(inputDTO));
    }

    @GetMapping("/specialties")
    @Operation(summary = "List predefined specialties")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<List<Specialty>> listSpecialties() {
        return ResponseEntity.ok(SpecialtyCatalog.all());
    }
}
