package com.das.cleanddd.domain.catalog.usecases.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.catalog.entities.IProductRepository;
import com.das.cleanddd.domain.catalog.ports.IProductEventPublisher;
import com.das.cleanddd.domain.catalog.usecases.dtos.AvailabilityOutputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.CreateProductInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.ProductIDDto;
import com.das.cleanddd.domain.catalog.usecases.dtos.ProductMapper;
import com.das.cleanddd.domain.catalog.usecases.dtos.ProductNamesInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.ProductOutputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.ReserveStockOutputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.StockQuantityInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.StockQuantityOutputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.UpdateProductInputDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.UseCaseOnlyInput;

@Service
public class ProductUseCaseFactory {

    private final IProductRepository productRepository;
    private final ProductMapper productMapper = new ProductMapper();

    private final CreateProductUseCase createProductUseCase;
    private final UpdateProductUseCase updateProductUseCase;
    private final ActivateProductUseCase activateProductUseCase;
    private final DeactivateProductUseCase deactivateProductUseCase;
    private final GetProductByIdUseCase getProductByIdUseCase;
    private final FindProductsByNameUseCase findProductsByNameUseCase;
    private final CheckProductAvailabilityUseCase checkProductAvailabilityUseCase;
    private final ReserveProductStockUseCase reserveProductStockUseCase;
    private final ReleaseProductStockUseCase releaseProductStockUseCase;
    private final RestockProductUseCase restockProductUseCase;

    public ProductUseCaseFactory(IProductRepository productRepository, IProductEventPublisher eventPublisher) {
        this.productRepository = productRepository;
        this.createProductUseCase = new CreateProductUseCase(this.productRepository, this.productMapper, eventPublisher);
        this.updateProductUseCase = new UpdateProductUseCase(this.productRepository, this.productMapper, eventPublisher);
        this.activateProductUseCase = new ActivateProductUseCase(this.productRepository, eventPublisher);
        this.deactivateProductUseCase = new DeactivateProductUseCase(this.productRepository, eventPublisher);
        this.getProductByIdUseCase = new GetProductByIdUseCase(this.productRepository, this.productMapper);
        this.findProductsByNameUseCase = new FindProductsByNameUseCase(this.productRepository, this.productMapper);
        this.checkProductAvailabilityUseCase = new CheckProductAvailabilityUseCase(this.productRepository);
        this.reserveProductStockUseCase = new ReserveProductStockUseCase(this.productRepository, eventPublisher);
        this.releaseProductStockUseCase = new ReleaseProductStockUseCase(this.productRepository, eventPublisher);
        this.restockProductUseCase = new RestockProductUseCase(this.productRepository, eventPublisher);
    }

    public UseCase<CreateProductInputDTO, ProductOutputDTO> getCreateProductUseCase() {
        return createProductUseCase;
    }
    public UseCase<UpdateProductInputDTO, ProductOutputDTO> getUpdateProductUseCase() {
        return updateProductUseCase;
    }
    public UseCaseOnlyInput<ProductIDDto> getActivateProductUseCase() {
        return activateProductUseCase;
    }
    public UseCaseOnlyInput<ProductIDDto> getDeactivateProductUseCase() {
        return deactivateProductUseCase;
    }
    public UseCase<ProductIDDto, ProductOutputDTO> getGetProductByIdUseCase() {
        return getProductByIdUseCase;
    }
    public UseCase<ProductNamesInputDTO, List<ProductOutputDTO>> getFindProductsByNameUseCase() {
        return findProductsByNameUseCase;
    }
    public UseCase<StockQuantityInputDTO, AvailabilityOutputDTO> getCheckProductAvailabilityUseCase() {
        return checkProductAvailabilityUseCase;
    }
    public UseCase<StockQuantityInputDTO, ReserveStockOutputDTO> getReserveProductStockUseCase() {
        return reserveProductStockUseCase;
    }
    public UseCase<StockQuantityInputDTO, StockQuantityOutputDTO> getReleaseProductStockUseCase() {
        return releaseProductStockUseCase;
    }
    public UseCase<StockQuantityInputDTO, StockQuantityOutputDTO> getRestockProductUseCase() {
        return restockProductUseCase;
    }
}
