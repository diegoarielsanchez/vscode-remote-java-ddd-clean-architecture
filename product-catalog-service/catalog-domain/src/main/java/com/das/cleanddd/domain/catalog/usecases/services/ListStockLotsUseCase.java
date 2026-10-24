package com.das.cleanddd.domain.catalog.usecases.services;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import com.das.cleanddd.domain.catalog.entities.IProductRepository;
import com.das.cleanddd.domain.catalog.entities.Product;
import com.das.cleanddd.domain.catalog.lot.IStockLotRepository;
import com.das.cleanddd.domain.catalog.lot.ShelfLifePolicy;
import com.das.cleanddd.domain.catalog.lot.StockLot;
import com.das.cleanddd.domain.catalog.usecases.dtos.ProductIDDto;
import com.das.cleanddd.domain.catalog.usecases.dtos.StockLotOutputDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/** A product's lots in the order they would be delivered (first expiry first out), empty ones included. */
public final class ListStockLotsUseCase implements UseCase<ProductIDDto, List<StockLotOutputDTO>> {

    private final IProductRepository products;
    private final IStockLotRepository lots;
    private final ShelfLifePolicy policy;
    private final Clock clock;

    public ListStockLotsUseCase(IProductRepository products, IStockLotRepository lots, ShelfLifePolicy policy, Clock clock) {
        this.products = products;
        this.lots = lots;
        this.policy = policy;
        this.clock = clock;
    }

    @Override
    public List<StockLotOutputDTO> execute(ProductIDDto input) throws DomainException {
        Product product = ProductUseCaseSupport.requireProduct(products, input == null ? null : input.productId());
        LocalDate today = LocalDate.now(clock);
        return lots.findByProductId(product.getId()).stream()
                .sorted(StockLot.FEFO)
                .map(lot -> ProductUseCaseSupport.output(lot, today, policy))
                .toList();
    }
}
