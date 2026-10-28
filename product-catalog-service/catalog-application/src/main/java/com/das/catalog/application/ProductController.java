package com.das.catalog.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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

import com.das.cleanddd.domain.catalog.usecases.dtos.AvailabilityOutputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.CreateProductInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.ExcursionInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.ExcursionOutputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.LotInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.LotQuantityInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.LotRecipientOutputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.ProductIDDto;
import com.das.cleanddd.domain.catalog.usecases.dtos.ProductNamesInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.ProductOutputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.ReceiveStockLotInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.ReserveStockOutputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.StockLotOutputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.StockQuantityInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.StockQuantityOutputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.UpdateProductInputDTO;
import com.das.cleanddd.domain.catalog.usecases.services.ProductUseCaseFactory;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.UseCaseOnlyInput;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@CrossOrigin(exposedHeaders = "errors, content-type")
@RequestMapping("api/v1/products")
@Tag(name = "Product Catalog", description = "API for managing the Pharma Lab product catalog")
@SecurityRequirement(name = "bearerAuth")
public class ProductController {

    private final UseCase<CreateProductInputDTO, ProductOutputDTO> createProductUseCase;
    private final UseCase<UpdateProductInputDTO, ProductOutputDTO> updateProductUseCase;
    private final UseCaseOnlyInput<ProductIDDto> activateProductUseCase;
    private final UseCaseOnlyInput<ProductIDDto> deactivateProductUseCase;
    private final UseCase<ProductIDDto, ProductOutputDTO> getProductByIdUseCase;
    private final UseCase<ProductNamesInputDTO, List<ProductOutputDTO>> findProductsByNameUseCase;
    private final UseCase<StockQuantityInputDTO, AvailabilityOutputDTO> checkProductAvailabilityUseCase;
    private final UseCase<StockQuantityInputDTO, ReserveStockOutputDTO> reserveProductStockUseCase;
    private final UseCase<LotQuantityInputDTO, StockQuantityOutputDTO> releaseProductStockUseCase;
    private final UseCase<ReceiveStockLotInputDTO, StockLotOutputDTO> receiveStockLotUseCase;
    private final UseCase<ProductIDDto, List<StockLotOutputDTO>> listStockLotsUseCase;
    private final UseCase<LotInputDTO, StockLotOutputDTO> quarantineLotUseCase;
    private final UseCase<LotInputDTO, StockLotOutputDTO> liftLotQuarantineUseCase;
    private final UseCase<LotInputDTO, List<LotRecipientOutputDTO>> findLotRecipientsUseCase;
    private final UseCase<ExcursionInputDTO, ExcursionOutputDTO> recordExcursionUseCase;
    private final UseCase<LotInputDTO, StockLotOutputDTO> rejectLotUseCase;

    public ProductController(ProductUseCaseFactory factory) {
        this.createProductUseCase = factory.getCreateProductUseCase();
        this.updateProductUseCase = factory.getUpdateProductUseCase();
        this.activateProductUseCase = factory.getActivateProductUseCase();
        this.deactivateProductUseCase = factory.getDeactivateProductUseCase();
        this.getProductByIdUseCase = factory.getGetProductByIdUseCase();
        this.findProductsByNameUseCase = factory.getFindProductsByNameUseCase();
        this.checkProductAvailabilityUseCase = factory.getCheckProductAvailabilityUseCase();
        this.reserveProductStockUseCase = factory.getReserveProductStockUseCase();
        this.releaseProductStockUseCase = factory.getReleaseProductStockUseCase();
        this.receiveStockLotUseCase = factory.getReceiveStockLotUseCase();
        this.listStockLotsUseCase = factory.getListStockLotsUseCase();
        this.quarantineLotUseCase = factory.getQuarantineLotUseCase();
        this.liftLotQuarantineUseCase = factory.getLiftLotQuarantineUseCase();
        this.findLotRecipientsUseCase = factory.getFindLotRecipientsUseCase();
        this.recordExcursionUseCase = factory.getRecordExcursionUseCase();
        this.rejectLotUseCase = factory.getRejectLotUseCase();
    }

    @PostMapping("/create")
    @Operation(summary = "Create product")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Object> createProduct(@Valid @RequestBody CreateProductInputDTO inputDTO) throws DomainException {
        return ResponseEntity.status(HttpStatus.CREATED).body(createProductUseCase.execute(inputDTO));
    }

    @PutMapping("/update")
    @Operation(summary = "Update product details and GTIN (stock is never touched here)")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Object> updateProduct(@Valid @RequestBody UpdateProductInputDTO inputDTO) throws DomainException {
        return ResponseEntity.ok(updateProductUseCase.execute(inputDTO));
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate product")
    @ResponseStatus(HttpStatus.OK)
    public void activateProduct(@PathVariable String id) throws DomainException {
        activateProductUseCase.execute(new ProductIDDto(id));
    }

    @PostMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate product")
    @ResponseStatus(HttpStatus.OK)
    public void deactivateProduct(@PathVariable String id) throws DomainException {
        deactivateProductUseCase.execute(new ProductIDDto(id));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Object> getProductByID(@PathVariable String id) throws DomainException {
        return ResponseEntity.ok(getProductByIdUseCase.execute(new ProductIDDto(id)));
    }

    @PostMapping("/list")
    @Operation(summary = "List products by name")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Object> findProductsByName(
        @RequestParam(name = "name", required = false, defaultValue = "") String name,
        @RequestParam(required = false, defaultValue = "1") int page,
        @RequestParam(required = false, defaultValue = "10") int pageSize
    ) throws DomainException {
        pageSize = Math.min(pageSize, 100);
        ProductNamesInputDTO inputDTO = new ProductNamesInputDTO(name, page, pageSize);
        return ResponseEntity.ok(findProductsByNameUseCase.execute(inputDTO));
    }

    /**
     * Minimal endpoint for internal service-to-service checks (e.g. order-service
     * deciding whether it's worth attempting a reservation). Returns only a
     * boolean — no price or exact stock count — intentionally unauthenticated so
     * peer microservices do not need a user JWT for a pure read-only check.
     * The API Gateway is the external auth boundary.
     */
    @GetMapping("/{id}/availability")
    @Operation(summary = "Check if a product is active and its deliverable lots hold at least the requested quantity (internal use)")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<AvailabilityOutputDTO> getAvailability(
        @PathVariable String id,
        @RequestParam(defaultValue = "1") @Min(1) int quantity
    ) throws DomainException {
        return ResponseEntity.ok(checkProductAvailabilityUseCase.execute(new StockQuantityInputDTO(id, quantity)));
    }

    /**
     * Mutates real inventory, so — unlike {@code availability} — this stays behind
     * the normal hasRole("USER") gate. Callers (e.g. order-service) forward the
     * requesting user's JWT through rather than calling unauthenticated.
     */
    @PostMapping("/{id}/reserve-stock")
    @Operation(summary = "Reserve stock for a product (internal use — mutating, requires a forwarded JWT)")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<ReserveStockOutputDTO> reserveStock(
        @PathVariable String id,
        @Valid @RequestBody QuantityRequest body
    ) throws DomainException {
        return ResponseEntity.ok(reserveProductStockUseCase.execute(new StockQuantityInputDTO(id, body.quantity())));
    }

    /** {@code batchNumber} may be left out for a product that is not lot-tracked: its units go back to its UNBATCHED lot. */
    @PostMapping("/{id}/release-stock")
    @Operation(summary = "Give manually reserved units back to the batch they came from (internal use)")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<StockQuantityOutputDTO> releaseStock(
        @PathVariable String id,
        @Valid @RequestBody LotQuantityRequest body
    ) throws DomainException {
        return ResponseEntity.ok(releaseProductStockUseCase.execute(
                new LotQuantityInputDTO(id, body.batchNumber(), body.quantity())));
    }

    /**
     * Goods receipt of one batch. Give {@code batchNumber} and {@code expirationDate}, or the GS1 code scanned from
     * the pack in {@code gs1} (e.g. {@code (01)09506000134352(17)270331(10)L2026-07A}), or both when they agree.
     * A product that is not lot-tracked (promotional material) needs neither: give only the quantity, or a batch
     * number (e.g. the approval code) with or without the date its approval ends.
     */
    @PostMapping("/{id}/restock")
    @Operation(summary = "Receive units of one batch into stock (goods receipt; GS1 code accepted)")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<StockLotOutputDTO> restock(
        @PathVariable String id,
        @Valid @RequestBody ReceiveLotRequest body
    ) throws DomainException {
        return ResponseEntity.ok(receiveStockLotUseCase.execute(new ReceiveStockLotInputDTO(
                id, body.quantity(), body.batchNumber(), body.expirationDate(), body.gs1())));
    }

    @GetMapping("/{id}/lots")
    @Operation(summary = "List the product's lots, first expiry first out")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<List<StockLotOutputDTO>> listLots(@PathVariable String id) throws DomainException {
        return ResponseEntity.ok(listStockLotsUseCase.execute(new ProductIDDto(id)));
    }

    @PostMapping("/{id}/lots/quarantine")
    @Operation(summary = "Block a lot (recall, quality investigation): its units are no longer delivered")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<StockLotOutputDTO> quarantineLot(
        @PathVariable String id,
        @Valid @RequestBody LotChangeRequest body
    ) throws DomainException {
        return ResponseEntity.ok(quarantineLotUseCase.execute(new LotInputDTO(id, body.batchNumber(), body.reason())));
    }

    @PostMapping("/{id}/lots/lift-quarantine")
    @Operation(summary = "Lift a lot's quarantine: its units can be delivered again")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<StockLotOutputDTO> liftLotQuarantine(
        @PathVariable String id,
        @Valid @RequestBody LotChangeRequest body
    ) throws DomainException {
        return ResponseEntity.ok(liftLotQuarantineUseCase.execute(new LotInputDTO(id, body.batchNumber(), body.reason())));
    }

    /**
     * Readings from a data logger or monitoring system for one lot. A reading outside the product's storage
     * conditions puts an available lot in quarantine until Quality lifts it or rejects the lot.
     */
    @PostMapping("/{id}/lots/excursions")
    @Operation(summary = "Record logger readings for a lot; a breach of the storage conditions quarantines it")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<ExcursionOutputDTO> recordExcursion(
        @PathVariable String id,
        @Valid @RequestBody ExcursionRequest body
    ) throws DomainException {
        return ResponseEntity.ok(recordExcursionUseCase.execute(new ExcursionInputDTO(id, body.batchNumber(),
                body.source(), body.observedFrom(), body.observedTo(), body.minTemperatureC(), body.maxTemperatureC(),
                body.maxHumidityPercent(), body.lightExposed(), body.note())));
    }

    /** Quality's final decision on a quarantined lot: its units leave the stock on hand and await destruction. */
    @PostMapping("/{id}/lots/reject")
    @Operation(summary = "Reject a quarantined lot: its units can no longer be used")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<StockLotOutputDTO> rejectLot(
        @PathVariable String id,
        @Valid @RequestBody LotChangeRequest body
    ) throws DomainException {
        return ResponseEntity.ok(rejectLotUseCase.execute(new LotInputDTO(id, body.batchNumber(), body.reason())));
    }

    /**
     * Recall: which orders received units of the batch. The batch goes in the query string, URL-encoded; leave it
     * out for the UNBATCHED lot of a product that is not lot-tracked.
     */
    @GetMapping("/{id}/lots/recipients")
    @Operation(summary = "List the orders that received units of a lot (recall)")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<List<LotRecipientOutputDTO>> lotRecipients(
        @PathVariable String id,
        @RequestParam(required = false) String batchNumber
    ) throws DomainException {
        return ResponseEntity.ok(findLotRecipientsUseCase.execute(new LotInputDTO(id, batchNumber, null)));
    }

    public record QuantityRequest(@Min(1) int quantity) {}

    /** {@code batchNumber} is required for a lot-tracked product; the use case checks it. */
    public record LotQuantityRequest(@Min(1) int quantity, String batchNumber) {}

    /** {@code expirationDate} is ISO {@code yyyy-MM-dd}; {@code gs1} is the pack's GS1 element string. */
    public record ReceiveLotRequest(@Min(1) int quantity, String batchNumber, LocalDate expirationDate,
                                    @Size(max = 200) String gs1) {}

    /** {@code batchNumber} is required for a lot-tracked product; the use case checks it. */
    public record LotChangeRequest(String batchNumber, @NotBlank @Size(max = 500) String reason) {}

    /**
     * {@code batchNumber} is required for a lot-tracked product; {@code source} names the logger, fridge or shipment.
     * Temperatures in °C; give at least one reading.
     */
    public record ExcursionRequest(String batchNumber, @NotBlank @Size(max = 100) String source,
                                   @NotNull Instant observedFrom, @NotNull Instant observedTo,
                                   BigDecimal minTemperatureC, BigDecimal maxTemperatureC, Integer maxHumidityPercent,
                                   Boolean lightExposed, @Size(max = 500) String note) {}
}
