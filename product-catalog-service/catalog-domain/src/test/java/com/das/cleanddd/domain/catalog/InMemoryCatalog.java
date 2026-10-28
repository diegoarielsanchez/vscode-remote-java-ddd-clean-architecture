package com.das.cleanddd.domain.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.das.cleanddd.domain.shared.storage.StorageCondition;
import com.das.cleanddd.domain.catalog.entities.Gtin;
import com.das.cleanddd.domain.catalog.entities.IProductRepository;
import com.das.cleanddd.domain.catalog.entities.Product;
import com.das.cleanddd.domain.catalog.entities.ProductActive;
import com.das.cleanddd.domain.catalog.entities.ProductDescription;
import com.das.cleanddd.domain.catalog.entities.ProductId;
import com.das.cleanddd.domain.catalog.entities.ProductName;
import com.das.cleanddd.domain.catalog.entities.ProductPrice;
import com.das.cleanddd.domain.catalog.entities.ProductStock;
import com.das.cleanddd.domain.catalog.entities.ProductUnit;
import com.das.cleanddd.domain.catalog.lot.BatchNumber;
import com.das.cleanddd.domain.catalog.lot.ExpirationDate;
import com.das.cleanddd.domain.catalog.lot.IStockLotRepository;
import com.das.cleanddd.domain.catalog.lot.LotAllocation;
import com.das.cleanddd.domain.catalog.lot.LotStatus;
import com.das.cleanddd.domain.catalog.lot.StockLot;
import com.das.cleanddd.domain.catalog.reservation.entities.IStockReservationRepository;
import com.das.cleanddd.domain.catalog.reservation.entities.LotRecipient;
import com.das.cleanddd.domain.catalog.reservation.entities.ReservationLine;
import com.das.cleanddd.domain.catalog.reservation.entities.StockReservation;
import com.das.cleanddd.domain.shared.criteria.Criteria;

/**
 * In-memory products, lots and reservations, with the same semantics as the SQL adapters: stock on hand is the sum of
 * the lots, a take succeeds only from an AVAILABLE lot that holds enough, and saving a lot never writes its quantity.
 */
public final class InMemoryCatalog implements IProductRepository, IStockLotRepository, IStockReservationRepository {

    /** Far enough ahead for lots that should never be close to expiry in a test. */
    public static final LocalDate FAR_FUTURE = LocalDate.of(2099, 12, 31);
    private static final Instant RECEIVED = Instant.parse("2026-01-01T00:00:00Z");

    private final Map<String, Product> products = new HashMap<>();
    private final Map<String, StockLot> lots = new LinkedHashMap<>();
    public final Map<String, StockReservation> reservations = new HashMap<>();
    private int receipts;

    /** A product with one far-future lot holding {@code stock} (none when 0). */
    public ProductId add(String name, String price, int stock, boolean active) throws Exception {
        ProductId id = addProduct(name, price, active, null);
        if (stock > 0) addLot(id, "L-" + id.value().substring(0, 8), FAR_FUTURE, stock);
        return id;
    }

    public ProductId addProduct(String name, String price, boolean active, String gtin) throws Exception {
        ProductId id = ProductId.random();
        products.put(id.value(), new Product(id, new ProductName(name), new ProductDescription("Test product"),
                new ProductPrice(new BigDecimal(price)), new ProductUnit("BOX"), new ProductStock(0),
                new ProductActive(active), gtin == null ? null : new Gtin(gtin)));
        return id;
    }

    public void setStorageCondition(ProductId id, StorageCondition condition) throws Exception {
        Product p = products.get(id.value());
        products.put(id.value(), new Product(p.getId(), p.getName(), p.getDescription(), p.getPrice(), p.getUnit(),
                p.getStock(), p.getActive(), p.getGtin(), p.isLotTracked(), condition));
    }

    /** Lots received later sort after earlier ones with the same expiry. */
    public void addLot(ProductId id, String batch, LocalDate expiry, int quantity) {
        String lotId = UUID.randomUUID().toString();
        lots.put(lotId, new StockLot(lotId, id, new BatchNumber(batch), expiry == null ? null : new ExpirationDate(expiry),
                quantity, LotStatus.AVAILABLE, RECEIVED.plusSeconds(receipts++)));
    }

    public void quarantine(ProductId id, String batch) {
        StockLot lot = findByProductIdAndBatchNumber(id, new BatchNumber(batch)).orElseThrow();
        lots.put(lot.id(), lot.withStatus(LotStatus.QUARANTINED));
    }

    public int stock(ProductId id) {
        return onHand(id);
    }

    public int lotQuantity(ProductId id, String batch) {
        return findByProductIdAndBatchNumber(id, new BatchNumber(batch)).orElseThrow().quantity();
    }

    // ── IProductRepository ───────────────────────────────────────────────────

    @Override public void save(Product product) { products.put(product.getId().value(), product); }

    @Override
    public Optional<Product> findById(ProductId id) {
        Product p = products.get(id.value());
        if (p == null) return Optional.empty();
        try {
            return Optional.of(new Product(p.getId(), p.getName(), p.getDescription(), p.getPrice(), p.getUnit(),
                    new ProductStock(onHand(id)), p.getActive(), p.getGtin(), p.isLotTracked(), p.getStorageCondition()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Optional<Product> findByGtin(Gtin gtin) {
        return products.values().stream().filter(p -> gtin.equals(p.getGtin())).findFirst()
                .flatMap(p -> findById(p.getId()));
    }

    @Override public List<Product> findByName(ProductName name, int page, int pageSize) { return List.of(); }
    @Override public List<Product> matching(Criteria criteria) { return List.of(); }
    @Override public List<Product> searchAll() { return new ArrayList<>(products.values()); }

    // ── IStockLotRepository ──────────────────────────────────────────────────

    @Override
    public List<StockLot> findByProductId(ProductId productId) {
        return lots.values().stream().filter(l -> l.productId().equals(productId)).toList();
    }

    @Override
    public Optional<StockLot> findByProductIdAndBatchNumber(ProductId productId, BatchNumber batchNumber) {
        return findByProductId(productId).stream().filter(l -> l.batchNumber().equals(batchNumber)).findFirst();
    }

    @Override
    public void save(StockLot lot) {
        StockLot existing = lots.get(lot.id());
        lots.put(lot.id(), existing == null ? lot : existing.withStatus(lot.status()));
    }

    @Override
    public boolean tryTake(StockLot lot, int quantity) {
        StockLot current = lots.get(lot.id());
        if (current == null || current.status() != LotStatus.AVAILABLE || current.quantity() < quantity) return false;
        lots.put(lot.id(), withQuantity(current, current.quantity() - quantity));
        return true;
    }

    @Override
    public void give(ProductId productId, String lotId, int quantity) {
        StockLot current = lots.get(lotId);
        lots.put(lotId, withQuantity(current, current.quantity() + quantity));
    }

    @Override
    public int onHand(ProductId productId) {
        return findByProductId(productId).stream().filter(l -> !l.isRejected()).mapToInt(StockLot::quantity).sum();
    }

    private static StockLot withQuantity(StockLot l, int quantity) {
        return new StockLot(l.id(), l.productId(), l.batchNumber(), l.expirationDate(), quantity, l.status(), l.receivedAt());
    }

    // ── IStockReservationRepository ──────────────────────────────────────────

    @Override public Optional<StockReservation> findByOrderId(String orderId) { return Optional.ofNullable(reservations.get(orderId)); }
    @Override public void save(StockReservation reservation) { reservations.put(reservation.orderId(), reservation); }

    @Override
    public List<LotRecipient> findRecipientsOfLot(String lotId) {
        List<LotRecipient> recipients = new ArrayList<>();
        reservations.values().forEach(r -> {
            int units = r.lines().stream().flatMap(l -> l.allocations().stream())
                    .filter(a -> a.lotId().equals(lotId)).mapToInt(LotAllocation::quantity).sum();
            if (units > 0) recipients.add(new LotRecipient(r.orderId(), r.status(), units));
        });
        return recipients;
    }

    /** Lots each line of the order's reservation came from, as "batch×units". */
    public List<List<String>> allocationsOf(String orderId) {
        return reservations.get(orderId).lines().stream()
                .map(ReservationLine::allocations)
                .map(as -> as.stream().map(a -> a.batchNumber() + "x" + a.quantity()).toList())
                .toList();
    }
}
