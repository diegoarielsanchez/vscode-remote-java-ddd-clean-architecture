package com.das.cleanddd.domain.catalog.usecases.services;

import com.das.cleanddd.domain.catalog.entities.IProductRepository;
import com.das.cleanddd.domain.catalog.entities.Product;
import com.das.cleanddd.domain.catalog.lot.StockAllocator;
import com.das.cleanddd.domain.catalog.usecases.dtos.AvailabilityOutputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.StockQuantityInputDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/**
 * Read-only check — never reserves stock. Available means the product is active and its deliverable lots (not
 * quarantined, keeping the minimum remaining shelf life) hold at least {@code quantity} units.
 */
public class CheckProductAvailabilityUseCase implements UseCase<StockQuantityInputDTO, AvailabilityOutputDTO> {

    private final IProductRepository repository;
    private final StockAllocator allocator;

    public CheckProductAvailabilityUseCase(IProductRepository repository, StockAllocator allocator) {
        this.repository = repository;
        this.allocator = allocator;
    }

    @Override
    public AvailabilityOutputDTO execute(StockQuantityInputDTO inputDTO) throws DomainException {
        Product p = ProductUseCaseSupport.requireProduct(repository, inputDTO.productId());
        boolean available = Boolean.TRUE.equals(p.isActive()) && allocator.allocatable(p.getId()) >= inputDTO.quantity();
        return new AvailabilityOutputDTO(available);
    }
}
