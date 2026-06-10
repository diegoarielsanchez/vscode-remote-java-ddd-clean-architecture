package com.das.settlement.application;

import java.io.InputStream;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import com.das.cleanddd.domain.settlement.usecases.dtos.CreateSettlementInputDTO;
import com.das.cleanddd.domain.settlement.usecases.dtos.InvoiceOutputDTO;
import com.das.cleanddd.domain.settlement.usecases.dtos.SettlementIDDto;
import com.das.cleanddd.domain.settlement.usecases.dtos.SettlementOutputDTO;
import com.das.cleanddd.domain.settlement.usecases.dtos.UpdateSettlementInputDTO;
import com.das.cleanddd.domain.settlement.usecases.dtos.AddInvoiceInputDTO;
import com.das.cleanddd.domain.settlement.usecases.dtos.ListSettlementsInputDTO;
import com.das.cleanddd.domain.settlement.usecases.dtos.RemoveInvoiceInputDTO;
import com.das.cleanddd.domain.settlement.usecases.services.SettlementUseCaseFactory;

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
    private static final long MAX_UPLOAD_BYTES = 10L * 1024 * 1024; // 10 MB — mirrors InvoiceFile domain rule
    private static final java.util.Set<String> ALLOWED_EXTENSIONS =
            java.util.Set.of(".pdf", ".xlsx", ".docx", ".txt");

    @Autowired
    private final UseCase<CreateSettlementInputDTO, SettlementOutputDTO> createSettlementUseCase;
    private final UseCase<UpdateSettlementInputDTO, SettlementOutputDTO> updateSettlementUseCase;
    private final UseCase<SettlementIDDto, SettlementOutputDTO> getSettlementByIdUseCase;
    private final UseCase<ListSettlementsInputDTO, List<SettlementOutputDTO>> listSettlementsUseCase;
    private final UseCase<AddInvoiceInputDTO, InvoiceOutputDTO> addInvoiceUseCase;
    private final UseCase<RemoveInvoiceInputDTO, SettlementOutputDTO> removeInvoiceUseCase;

    public SettlementController(SettlementUseCaseFactory settlementUseCaseFactory) {
        this.createSettlementUseCase = settlementUseCaseFactory.getCreateSettlementUseCase();
        this.updateSettlementUseCase = settlementUseCaseFactory.getUpdateSettlementUseCase();
        this.getSettlementByIdUseCase = settlementUseCaseFactory.getSettlementByIdUseCase();
        this.listSettlementsUseCase = settlementUseCaseFactory.getListSettlementsUseCase();
        this.addInvoiceUseCase = settlementUseCaseFactory.getAddInvoiceUseCase();
        this.removeInvoiceUseCase = settlementUseCaseFactory.getRemoveInvoiceUseCase();
    }

    @PostMapping("/create")
    @Operation(summary = "Create settlement")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Object> createSettlement(@Valid @RequestBody CreateSettlementInputDTO inputDTO) throws DomainException {
        log.info("POST /api/v1/settlement/create");
        return ResponseEntity.status(HttpStatus.CREATED).body(createSettlementUseCase.execute(inputDTO));
    }

    @PutMapping("/update")
    @Operation(summary = "Update settlement")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Object> updateSettlement(@Valid @RequestBody UpdateSettlementInputDTO inputDTO) throws DomainException {
        log.info("PUT /api/v1/settlement/update");
        return ResponseEntity.ok(updateSettlementUseCase.execute(inputDTO));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get settlement by ID")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Object> getSettlementById(@PathVariable String id) throws DomainException {
        log.info("GET /api/v1/settlement/{}", sanitize(id));
        return ResponseEntity.ok(getSettlementByIdUseCase.execute(new SettlementIDDto(id)));
    }

    @PostMapping("/list")
    @Operation(summary = "List settlements")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Object> listSettlements(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "10") int pageSize) throws DomainException {
        log.info("POST /api/v1/settlement/list");
        pageSize = Math.min(pageSize, 100);
        return ResponseEntity.ok(listSettlementsUseCase.execute(new ListSettlementsInputDTO(page, pageSize)));
    }

    @PostMapping(value = "/invoice/add", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Add invoice with file")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Object> addInvoice(
            @RequestParam("settlementId") String settlementId,
            @RequestParam("invoiceNumber") String invoiceNumber,
            @RequestParam("issueDate") @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate issueDate,
            @RequestParam(value = "dueDate", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate dueDate,
            @RequestParam("amount") java.math.BigDecimal amount,
            @RequestPart("file") MultipartFile file) throws DomainException, java.io.IOException {
        log.info("POST /api/v1/settlement/invoice/add settlementId={} invoiceNumber={}", sanitize(settlementId), sanitize(invoiceNumber));
        String originalFilename = file.getOriginalFilename();
        String ext = (originalFilename != null && originalFilename.contains("."))
                ? originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase()
                : "";
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new InvalidInputException(
                    "Unsupported file type. Allowed extensions: .pdf, .xlsx, .docx, .txt");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new InvalidInputException(
                    "Invoice file exceeds the maximum allowed size of 10 MB.");
        }
        final byte[] fileBytes;
        try (InputStream inputStream = file.getInputStream()) {
            fileBytes = inputStream.readAllBytes();
        }
        AddInvoiceInputDTO inputDTO = new AddInvoiceInputDTO(
                settlementId,
                invoiceNumber,
                issueDate,
                dueDate,
                amount,
                file.getOriginalFilename(),
                file.getContentType(),
                fileBytes);
        return ResponseEntity.status(HttpStatus.CREATED).body(addInvoiceUseCase.execute(inputDTO));
    }

    @DeleteMapping("/invoice/remove")
    @Operation(summary = "Remove invoice and its file")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Object> removeInvoice(@Valid @RequestBody RemoveInvoiceInputDTO inputDTO) throws DomainException {
        log.info("DELETE /api/v1/settlement/invoice/remove settlementId={} invoiceId={}",
                sanitize(inputDTO.settlementId()), sanitize(inputDTO.invoiceId()));
        return ResponseEntity.ok(removeInvoiceUseCase.execute(inputDTO));
    }

    /** Strips log-injection characters from user-supplied values (OWASP A09). */
    private static String sanitize(String value) {
        return value == null ? "" : value.replaceAll("[\r\n\t]", "_");
    }
}
