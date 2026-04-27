package com.das.msr.application;

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

import com.das.cleanddd.domain.medicalsalesrep.usecases.dtos.CreateMedicalSalesRepInputDTO;
import com.das.cleanddd.domain.medicalsalesrep.usecases.dtos.MedicalSalesRepNamesInputDTO;
import com.das.cleanddd.domain.medicalsalesrep.usecases.dtos.MedicalSalesRepIDDto;
import com.das.cleanddd.domain.medicalsalesrep.usecases.dtos.MedicalSalesRepOutputDTO;
import com.das.cleanddd.domain.medicalsalesrep.usecases.dtos.UpdateMedicalSalesRepInputDTO;
import com.das.cleanddd.domain.medicalsalesrep.usecases.services.MedicalSalesRepUseCaseFactory;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.UseCaseOnlyInput;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

@RestController
@CrossOrigin(exposedHeaders = "errors, content-type")
@RequestMapping("api/v1/medicalsalesrep")
@Tag(name = "Medical Sales Rep", description = "API for managing Medical Sales Representatives")
@SecurityRequirement(name = "bearerAuth")
public class MedicalSalesRepController {

    @Autowired
    private final UseCase<CreateMedicalSalesRepInputDTO, MedicalSalesRepOutputDTO> createMedicalSalesRepUseCase;
    private final UseCase<UpdateMedicalSalesRepInputDTO, MedicalSalesRepOutputDTO> updateMedicalSalesRepUseCase;
    private final UseCaseOnlyInput<MedicalSalesRepIDDto> activateMedicalSalesRepUseCase;
    private final UseCaseOnlyInput<MedicalSalesRepIDDto> deactivateMedicalSalesRepUseCase;
    private final UseCase<MedicalSalesRepIDDto, MedicalSalesRepOutputDTO> getGetMedicalSalesRepByIdUseCase;
    private final UseCase<MedicalSalesRepNamesInputDTO, List<MedicalSalesRepOutputDTO>> findMedicalSalesRepByNameUseCase;

    public MedicalSalesRepController(MedicalSalesRepUseCaseFactory medicalSalesRepUseCaseFactory) {
        this.createMedicalSalesRepUseCase = medicalSalesRepUseCaseFactory.getCreateMedicalSalesRepUseCase();
        this.updateMedicalSalesRepUseCase = medicalSalesRepUseCaseFactory.getUpdateMedicalSalesRepUseCase();
        this.activateMedicalSalesRepUseCase = medicalSalesRepUseCaseFactory.getActivateMedicalSalesRepUseCase();
        this.deactivateMedicalSalesRepUseCase = medicalSalesRepUseCaseFactory.getDeActivateMedicalSalesRepUseCase();
        this.getGetMedicalSalesRepByIdUseCase = medicalSalesRepUseCaseFactory.getGetMedicalSalesRepByIdUseCase();
        this.findMedicalSalesRepByNameUseCase = medicalSalesRepUseCaseFactory.findMedicalSalesRepByNameUseCase();
    }

    @PostMapping("/create")
    @Operation(summary = "Create medical sales representative")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Object> createMedicalSalesRep(@Valid @RequestBody CreateMedicalSalesRepInputDTO inputDTO) throws DomainException {
        return ResponseEntity.status(HttpStatus.CREATED).body(createMedicalSalesRepUseCase.execute(inputDTO));
    }

    @PutMapping("/update")
    @Operation(summary = "Update medical sales representative")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Object> updateMedicalSalesRep(@Valid @RequestBody UpdateMedicalSalesRepInputDTO inputDTO) throws DomainException {
        return ResponseEntity.ok(updateMedicalSalesRepUseCase.execute(inputDTO));
    }

    @PostMapping("/activate")
    @Operation(summary = "Activate medical sales representative")
    @ResponseStatus(HttpStatus.OK)
    public void activateMedicalSalesRep(@Valid @RequestBody MedicalSalesRepIDDto inputDTO) throws DomainException {
        activateMedicalSalesRepUseCase.execute(inputDTO);
    }

    @PostMapping("/deactivate")
    @Operation(summary = "Deactivate medical sales representative")
    @ResponseStatus(HttpStatus.OK)
    public void deactivateMedicalSalesRep(@Valid @RequestBody MedicalSalesRepIDDto inputDTO) throws DomainException {
        deactivateMedicalSalesRepUseCase.execute(inputDTO);
    }

    @GetMapping("/get")
    @Operation(summary = "Get medical sales representative by ID")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Object> getMedicalSalesRepByID(@Valid @RequestBody MedicalSalesRepIDDto inputDTO) throws DomainException {
        return ResponseEntity.ok(getGetMedicalSalesRepByIdUseCase.execute(inputDTO));
    }

    @PostMapping("/list")
    @Operation(summary = "List medical sales representatives by name")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Object> findMedicalSalesRepByName(
        @RequestParam(required = false, defaultValue = "") String firstName,
        @RequestParam(required = false, defaultValue = "") String lastName,
        @RequestParam(required = false, defaultValue = "1") int page,
        @RequestParam(required = false, defaultValue = "10") int pageSize
    ) throws DomainException {
        MedicalSalesRepNamesInputDTO inputDTO = new MedicalSalesRepNamesInputDTO(firstName, lastName, page, pageSize);
        return ResponseEntity.ok(findMedicalSalesRepByNameUseCase.execute(inputDTO));
    }
}
