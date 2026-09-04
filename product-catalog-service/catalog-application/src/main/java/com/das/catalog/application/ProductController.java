package com.das.catalog.application;

import java.util.List;

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

import com.das.cleanddd.domain.catalog.usecases.dtos.AvailabilityOutputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.CreateProductInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.ProductIDDto;
import com.das.cleanddd.domain.catalog.usecases.dtos.ProductNamesInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.ProductOutputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.ReserveStockOutputDTO;
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

@RestController
@CrossOrigin(exposedHeaders = "errors, content-type")
@RequestMapping("api/v1/products")
@Tag(name = "Product Catalog", description = "API for managing the Pharma Lab product catalog")
@SecurityRequirement(name = "bearerAuth")
public class ProductController {

    @Autowired
    private final UseCase<CreateProductInputDTO, ProductOutputDTO> createProductUseCase;
    private final UseCase<UpdateProductInputDTO, ProductOutputDTO> updateProductUseCase;
    private final UseCaseOnlyInput<ProductIDDto> activateProductUseCase;
    private final UseCaseOnlyInput<ProductIDDto> deactivateProductUseCase;
    private final UseCase<ProductIDDto, ProductOutputDTO> getProductByIdUseCase;
    private final UseCase<ProductNamesInputDTO, List<ProductOutputDTO>> findProductsByNameUseCase;
    private final UseCase<StockQuantityInputDTO, AvailabilityOutputDTO> checkProductAvailabilityUseCase;
    private final UseCase<StockQuantityInputDTO, ReserveStockOutputDTO> reserveProductStockUseCase;
    private final UseCase<StockQuantityInputDTO, StockQuantityOutputDTO> releaseProductStockUseCase;
    private final UseCase<StockQuantityInputDTO, StockQuantityOutputDTO> restockProductUseCase;

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
        this.restockProductUseCase = factory.getRestockProductUseCase();
    }

    @PostMapping("/create")
    @Operation(summary = "Create product")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Object> createProduct(@Valid @RequestBody CreateProductInputDTO inputDTO) throws DomainException {
        return ResponseEntity.status(HttpStatus.CREATED).body(createProductUseCase.execute(inputDTO));
    }

    @PutMapping("/update")
    @Operation(summary = "Update product (name/description/price/unit only — stock is never touched here)")
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
    @Operation(summary = "Check if a product is active and has at least the requested quantity on hand (internal use)")
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

    @PostMapping("/{id}/release-stock")
    @Operation(summary = "Release previously-reserved stock for a product (internal use — mutating, requires a forwarded JWT)")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<StockQuantityOutputDTO> releaseStock(
        @PathVariable String id,
        @Valid @RequestBody QuantityRequest body
    ) throws DomainException {
        return ResponseEntity.ok(releaseProductStockUseCase.execute(new StockQuantityInputDTO(id, body.quantity())));
    }

    @PostMapping("/{id}/restock")
    @Operation(summary = "Add stock to a product (manual/admin inventory top-up)")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<StockQuantityOutputDTO> restock(
        @PathVariable String id,
        @Valid @RequestBody QuantityRequest body
    ) throws DomainException {
        return ResponseEntity.ok(restockProductUseCase.execute(new StockQuantityInputDTO(id, body.quantity())));
    }

    public record QuantityRequest(@Min(1) int quantity) {}
}
