package com.das.cleanddd.domain.catalog.entities;

import com.das.cleanddd.domain.catalog.events.ProductActivatedEvent;
import com.das.cleanddd.domain.catalog.events.ProductCreatedEvent;
import com.das.cleanddd.domain.catalog.events.ProductDeactivatedEvent;
import com.das.cleanddd.domain.catalog.events.ProductDomainEvent;
import com.das.cleanddd.domain.catalog.events.ProductUpdatedEvent;
import com.das.cleanddd.domain.shared.AggregateRoot;
import com.das.cleanddd.domain.shared.UtilsFactory;
import com.das.cleanddd.domain.shared.ValidationUtils;
import com.das.cleanddd.domain.shared.exceptions.BusinessException;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;
import com.das.cleanddd.domain.shared.exceptions.RequiredFieldException;

public class Product extends AggregateRoot<ProductDomainEvent> {

    private final ProductId _id;
    private final ProductName _name;
    private final ProductDescription _description;
    private final ProductPrice _price;
    private final ProductUnit _unit;
    private final ProductStock _stock;
    private final ProductActive _active;
    private final transient ValidationUtils _validationUtils;

    /** Description is optional: a product may have none on file yet. */
    public Product(ProductId id, ProductName name, ProductDescription description, ProductPrice price,
                    ProductUnit unit, ProductStock stock, ProductActive active) throws BusinessValidationException {
        this._id = id == null ? ProductId.random() : id;
        this._name = name;
        this._description = description;
        this._price = price;
        this._unit = unit;
        this._stock = stock == null ? new ProductStock(0) : stock;
        this._active = active == null ? new ProductActive(false) : active;
        this._validationUtils = (new UtilsFactory()).getValidationUtils();
    }

    public ProductId getId() {
        return this._id;
    }

    public ProductName getName() {
        return this._name;
    }

    public ProductDescription getDescription() {
        return this._description;
    }

    public ProductPrice getPrice() {
        return this._price;
    }

    public ProductUnit getUnit() {
        return this._unit;
    }

    public ProductStock getStock() {
        return this._stock;
    }

    public ProductActive getActive() {
        return this._active;
    }

    public Boolean isActive() {
        return this._active != null && Boolean.TRUE.equals(this._active.value()) ? Boolean.TRUE : Boolean.FALSE;
    }

    public static Product create(ProductId id, ProductName name, ProductDescription description, ProductPrice price,
                                  ProductUnit unit, ProductStock stock) throws BusinessValidationException {
        Product product = new Product(id, name, description, price, unit, stock, new ProductActive(false));
        product.record(new ProductCreatedEvent(
                product.getId().value(),
                product.getName().value(),
                product.getDescription() == null ? null : product.getDescription().value(),
                product.getPrice().value(),
                product.getUnit().value(),
                product.getActive().value()));
        return product;
    }

    public void validate() throws BusinessException {
        if (this._validationUtils.isNull(this._id)) throw new RequiredFieldException("id");
        if (this._validationUtils.isNull(this._name) || this._validationUtils.isNullOrEmpty(this._name.value())) {
            throw new RequiredFieldException("name");
        }
        if (this._validationUtils.isNull(this._price)) throw new RequiredFieldException("price");
        if (this._validationUtils.isNull(this._unit) || this._validationUtils.isNullOrEmpty(this._unit.value())) {
            throw new RequiredFieldException("unit");
        }
        if (this._validationUtils.isNull(this._stock)) throw new RequiredFieldException("stock");
        if (this._validationUtils.isNull(this._active)) throw new RequiredFieldException("active");
        // Description is optional — no requiredness check.
    }

    public Product setActivate() throws BusinessValidationException {
        if (this._active != null && this._active.value()) {
            return this;
        }
        Product activated = new Product(this._id, this._name, this._description, this._price, this._unit,
                this._stock, new ProductActive(true));
        activated.record(new ProductActivatedEvent(activated.getId().value(), activated.getActive().value()));
        return activated;
    }

    public Product setDeactivate() throws BusinessValidationException {
        if (this._active != null && !this._active.value()) {
            return this;
        }
        Product deactivated = new Product(this._id, this._name, this._description, this._price, this._unit,
                this._stock, new ProductActive(false));
        deactivated.record(new ProductDeactivatedEvent(deactivated.getId().value(), deactivated.getActive().value()));
        return deactivated;
    }

    /** Never touches stock — stock is only ever mutated via the atomic reserve/release/restock repository operations. */
    public Product withUpdatedDetails(ProductName name, ProductDescription description, ProductPrice price,
                                       ProductUnit unit) throws BusinessValidationException {
        Product updated = new Product(this._id, name, description, price, unit, this._stock, this._active);
        updated.record(new ProductUpdatedEvent(
                updated.getId().value(),
                updated.getName().value(),
                updated.getDescription() == null ? null : updated.getDescription().value(),
                updated.getPrice().value(),
                updated.getUnit().value(),
                updated.getActive().value()));
        return updated;
    }

    public ProductId id() {
        return this._id;
    }
}
