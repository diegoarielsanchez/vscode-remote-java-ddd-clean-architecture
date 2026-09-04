package com.das.infra.service.catalog;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.catalog.entities.IProductRepository;
import com.das.cleanddd.domain.catalog.entities.Product;
import com.das.cleanddd.domain.catalog.entities.ProductActive;
import com.das.cleanddd.domain.catalog.entities.ProductDescription;
import com.das.cleanddd.domain.catalog.entities.ProductId;
import com.das.cleanddd.domain.catalog.entities.ProductName;
import com.das.cleanddd.domain.catalog.entities.ProductPrice;
import com.das.cleanddd.domain.catalog.entities.ProductStock;
import com.das.cleanddd.domain.catalog.entities.ProductUnit;
import com.das.cleanddd.domain.shared.criteria.Criteria;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;

@Primary
@Service
public final class SQLProductRepository implements IProductRepository {

    @Autowired
    private ProductJpaRepository jpaRepository;

    @Override
    public List<Product> searchAll() {
        return jpaRepository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void save(Product product) {
        ProductEntity entity = toEntity(product);
        if (entity != null) {
            // toEntity() always builds a fresh, version-less instance from the domain
            // object. With @Version on ProductEntity, saving that as-is would make
            // Hibernate treat every update as a brand-new row (isNew() looks at the
            // version field). Carrying the current row's version forward lets an
            // existing product update correctly — and lets @Version do its job of
            // rejecting a concurrent conflicting update — while a genuinely new id
            // still inserts with version left null.
            jpaRepository.findById(entity.getId()).ifPresent(existing -> entity.setVersion(existing.getVersion()));
            jpaRepository.save(entity);
        }
    }

    @Override
    public List<Product> matching(Criteria criteria) {
        // Implement criteria-based search if needed
        return null;
    }

    @Override
    public List<Product> findByName(ProductName name, int page, int pageSize) {
        String nameValue = (name != null && !name.value().isBlank()) ? name.value() : "";
        List<ProductEntity> entities = jpaRepository.findByNameStartingWith(nameValue);
        return entities.stream()
                .map(this::toDomain)
                .skip((long) (page - 1) * pageSize)
                .limit(pageSize)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Product> findById(ProductId identifier) {
        String id = identifier.value();
        if (id == null) {
            return Optional.empty();
        }
        return jpaRepository.findById(id)
                .map(this::toDomain);
    }

    @Override
    public boolean tryReserveStock(ProductId id, int quantity) {
        return jpaRepository.reserveStock(id.value(), quantity) == 1;
    }

    @Override
    public void releaseStock(ProductId id, int quantity) {
        jpaRepository.addStock(id.value(), quantity);
    }

    @Override
    public void restock(ProductId id, int quantity) {
        jpaRepository.addStock(id.value(), quantity);
    }

    private Product toDomain(ProductEntity entity) {
        try {
            return new Product(
                    new ProductId(entity.getId()),
                    new ProductName(entity.getName()),
                    entity.getDescription() == null ? null : new ProductDescription(entity.getDescription()),
                    new ProductPrice(entity.getPrice()),
                    new ProductUnit(entity.getUnit()),
                    new ProductStock(entity.getStock()),
                    new ProductActive(entity.getActive()));
        } catch (BusinessValidationException e) {
            // A persisted row has already passed these invariants once; a violation here
            // means the stored data itself is corrupt, which is an infrastructure failure,
            // not a domain error the caller can recover from.
            throw new IllegalStateException("Corrupt product record " + entity.getId() + ": " + e.getMessage(), e);
        }
    }

    private ProductEntity toEntity(Product domain) {
        ProductEntity entity = new ProductEntity();
        entity.setId(domain.id().value());
        entity.setName(domain.getName().value());
        entity.setDescription(domain.getDescription() == null ? null : domain.getDescription().value());
        entity.setPrice(domain.getPrice().value());
        entity.setUnit(domain.getUnit().value());
        entity.setStock(domain.getStock().value());
        entity.setActive(domain.getActive().value());
        return entity;
    }
}
