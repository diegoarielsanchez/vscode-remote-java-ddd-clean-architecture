package com.das.cleanddd.domain.catalog.reservation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.das.cleanddd.domain.catalog.entities.IProductRepository;
import com.das.cleanddd.domain.catalog.entities.Product;
import com.das.cleanddd.domain.catalog.entities.ProductActive;
import com.das.cleanddd.domain.catalog.entities.ProductDescription;
import com.das.cleanddd.domain.catalog.entities.ProductId;
import com.das.cleanddd.domain.catalog.entities.ProductName;
import com.das.cleanddd.domain.catalog.entities.ProductPrice;
import com.das.cleanddd.domain.catalog.entities.ProductStock;
import com.das.cleanddd.domain.catalog.entities.ProductUnit;
import com.das.cleanddd.domain.catalog.reservation.entities.IStockReservationRepository;
import com.das.cleanddd.domain.catalog.reservation.entities.StockReservation;
import com.das.cleanddd.domain.shared.criteria.Criteria;

/** In-memory products and reservations, with the same conditional-decrement semantics as the SQL repository. */
final class InMemoryCatalog implements IProductRepository, IStockReservationRepository {

    private final Map<String, Product> products = new HashMap<>();
    final Map<String, StockReservation> reservations = new HashMap<>();

    ProductId add(String name, String price, int stock, boolean active) throws Exception {
        ProductId id = ProductId.random();
        products.put(id.value(), new Product(id, new ProductName(name), new ProductDescription("Test product"),
                new ProductPrice(new BigDecimal(price)), new ProductUnit("BOX"), new ProductStock(stock),
                new ProductActive(active)));
        return id;
    }

    int stock(ProductId id) {
        return products.get(id.value()).getStock().value();
    }

    private void setStock(ProductId id, int stock) {
        Product p = products.get(id.value());
        try {
            products.put(id.value(), new Product(p.getId(), p.getName(), p.getDescription(), p.getPrice(), p.getUnit(),
                    new ProductStock(stock), p.getActive()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override public void save(Product product) { products.put(product.getId().value(), product); }
    @Override public Optional<Product> findById(ProductId id) { return Optional.ofNullable(products.get(id.value())); }
    @Override public List<Product> findByName(ProductName name, int page, int pageSize) { return List.of(); }
    @Override public List<Product> matching(Criteria criteria) { return List.of(); }
    @Override public List<Product> searchAll() { return new ArrayList<>(products.values()); }

    @Override
    public boolean tryReserveStock(ProductId id, int quantity) {
        Product p = products.get(id.value());
        if (p == null || p.getStock().value() < quantity) return false;
        setStock(id, p.getStock().value() - quantity);
        return true;
    }

    @Override public void releaseStock(ProductId id, int quantity) { setStock(id, stock(id) + quantity); }
    @Override public void restock(ProductId id, int quantity) { setStock(id, stock(id) + quantity); }

    @Override public Optional<StockReservation> findByOrderId(String orderId) { return Optional.ofNullable(reservations.get(orderId)); }
    @Override public void save(StockReservation reservation) { reservations.put(reservation.orderId(), reservation); }
}
