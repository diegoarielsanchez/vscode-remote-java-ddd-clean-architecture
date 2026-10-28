package com.das.cleanddd.domain.catalog.usecases.services;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.catalog.entities.Gtin;
import com.das.cleanddd.domain.catalog.entities.IProductRepository;
import com.das.cleanddd.domain.catalog.entities.Product;
import com.das.cleanddd.domain.catalog.entities.ProductDescription;
import com.das.cleanddd.domain.catalog.entities.ProductId;
import com.das.cleanddd.domain.catalog.entities.ProductName;
import com.das.cleanddd.domain.catalog.entities.ProductPrice;
import com.das.cleanddd.domain.catalog.entities.ProductUnit;
import com.das.cleanddd.domain.catalog.ports.IProductEventPublisher;
import com.das.cleanddd.domain.catalog.usecases.dtos.ProductMapper;
import com.das.cleanddd.domain.catalog.usecases.dtos.ProductOutputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.UpdateProductInputDTO;
import com.das.cleanddd.domain.shared.audit.ChangeReason;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.UnitOfWork;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import com.das.cleanddd.domain.shared.storage.StorageCondition;

@Service
public final class UpdateProductUseCase implements UseCase<UpdateProductInputDTO, ProductOutputDTO> {

    @Autowired
    private final IProductRepository repository;
    @Autowired
    private final ProductMapper mapper;
    private final IProductEventPublisher publisher;
    private final UnitOfWork unitOfWork;

    public UpdateProductUseCase(IProductRepository repository, ProductMapper mapper, IProductEventPublisher publisher, UnitOfWork unitOfWork) {
        this.repository = repository;
        this.mapper = mapper;
        this.publisher = publisher;
        this.unitOfWork = unitOfWork;
    }

    /**
     * Loading, changing, saving and recording the events happen in one unit of work, so the
     * change and its outbox entries commit (or roll back) together.
     */
    @Override
    public ProductOutputDTO execute(UpdateProductInputDTO inputDTO) throws DomainException {
        return unitOfWork.execute(() -> doExecute(inputDTO));
    }

    private ProductOutputDTO doExecute(UpdateProductInputDTO inputDTO) throws DomainException {
        if (inputDTO == null) {
            throw new DomainException("Input DTO cannot be null");
        }
        Product product;
        try {
            ProductName name = new ProductName(inputDTO.name());
            ProductDescription description = inputDTO.description() == null
                    ? null : new ProductDescription(inputDTO.description());
            ProductPrice price = new ProductPrice(inputDTO.price());
            ProductUnit unit = new ProductUnit(inputDTO.unit());
            ProductId id = new ProductId(inputDTO.id());
            ChangeReason reason = new ChangeReason(inputDTO.reason());

            Optional<Product> existing = repository.findById(id);
            if (!existing.isPresent()) {
                throw new DomainException("Product not found.");
            }
            Gtin gtin = inputDTO.gtin() == null ? existing.get().getGtin() : new Gtin(inputDTO.gtin());
            if (gtin != null && !gtin.equals(existing.get().getGtin())) {
                ProductUseCaseSupport.requireUnusedGtin(repository, gtin, id);
            }
            StorageCondition storageCondition = inputDTO.storageCondition() == null
                    ? existing.get().getStorageCondition() : inputDTO.storageCondition().toDomain();
            // Never touches stock — stock is only ever mutated via the atomic
            // reserve/release/restock repository operations.
            product = existing.get().withUpdatedDetails(name, description, price, unit, gtin, storageCondition, reason);
            repository.save(product);
            product.pullDomainEvents().forEach(publisher::publish);
            return mapper.outputFromEntity(product);
        } catch (IllegalArgumentException e) {
            throw new DomainException(e.getMessage());
        }
    }
}
