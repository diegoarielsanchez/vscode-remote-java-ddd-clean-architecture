package com.das.cleanddd.domain.catalog.usecases.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.das.cleanddd.domain.catalog.entities.IProductRepository;
import com.das.cleanddd.domain.catalog.entities.Product;
import com.das.cleanddd.domain.catalog.entities.ProductActive;
import com.das.cleanddd.domain.catalog.entities.ProductId;
import com.das.cleanddd.domain.catalog.entities.ProductName;
import com.das.cleanddd.domain.catalog.entities.ProductPrice;
import com.das.cleanddd.domain.catalog.entities.ProductStock;
import com.das.cleanddd.domain.catalog.entities.ProductTraceability;
import com.das.cleanddd.domain.catalog.entities.ProductUnit;
import com.das.cleanddd.domain.catalog.ports.IProductEventPublisher;
import com.das.cleanddd.domain.catalog.usecases.dtos.CreateProductInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.ProductMapper;
import com.das.cleanddd.domain.catalog.usecases.dtos.ProductOutputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.StockQuantityInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.UpdateProductInputDTO;
import com.das.cleanddd.domain.shared.UnitOfWork;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/** "Today" is 2026-10-24; a product expiring on {@link #EXPIRES} is still usable. */
@ExtendWith(MockitoExtension.class)
@DisplayName("Batch number, serial number and expiration date through the product use cases")
class ProductTraceabilityUseCasesTest {

    private static final Clock TODAY = Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate EXPIRES = LocalDate.of(2027, 3, 31);
    private static final LocalDate EXPIRED = LocalDate.of(2026, 10, 23);

    @Mock private IProductRepository repository;
    @Mock private IProductEventPublisher publisher;

    private final ProductMapper mapper = new ProductMapper();
    private final ProductId id = ProductId.random();
    private CreateProductUseCase create;
    private UpdateProductUseCase update;

    @BeforeEach
    void setUp() {
        create = new CreateProductUseCase(repository, mapper, publisher, UnitOfWork.immediate());
        update = new UpdateProductUseCase(repository, mapper, publisher, UnitOfWork.immediate());
    }

    private Product stored(ProductTraceability traceability) throws Exception {
        return new Product(id, new ProductName("Insulin 100IU"), null, new ProductPrice(new BigDecimal("20.00")),
                new ProductUnit("VIAL"), new ProductStock(50), new ProductActive(true), traceability);
    }

    @Test
    @DisplayName("create stores and returns the three fields")
    void createStoresThem() throws DomainException {
        ProductOutputDTO out = create.execute(new CreateProductInputDTO("Insulin 100IU", null,
                new BigDecimal("20.00"), "VIAL", 50, "L2026-07A", "SN-000123", EXPIRES));

        assertEquals("L2026-07A", out.batchNumber());
        assertEquals("SN-000123", out.serialNumber());
        assertEquals(EXPIRES, out.expirationDate());
        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(repository).save(saved.capture());
        assertEquals("L2026-07A", saved.getValue().getTraceability().batchNumberValue());
    }

    @Test
    @DisplayName("create without them leaves them empty")
    void createWithoutThem() throws DomainException {
        ProductOutputDTO out = create.execute(new CreateProductInputDTO("Aspirin", null, new BigDecimal("1.00"), "BOX", 1));

        assertNull(out.batchNumber());
        assertNull(out.serialNumber());
        assertNull(out.expirationDate());
    }

    @Test
    @DisplayName("create refuses a malformed batch number and saves nothing")
    void createRefusesMalformedCodes() {
        DomainException e = assertThrows(DomainException.class, () -> create.execute(new CreateProductInputDTO(
                "Insulin 100IU", null, new BigDecimal("20.00"), "VIAL", 50, "LOT 7", null, null)));

        assertTrue(e.getMessage().startsWith("Batch number"), e.getMessage());
        verify(repository, never()).save(any());
        verify(publisher, never()).publish(any());
    }

    @Test
    @DisplayName("an update that leaves the fields out keeps them")
    void updateWithoutThemKeepsThem() throws Exception {
        when(repository.findById(id)).thenReturn(Optional.of(stored(ProductTraceability.of("L1", "SN-1", EXPIRES))));

        ProductOutputDTO out = update.execute(new UpdateProductInputDTO(id.value(), "Insulin 100IU", null,
                new BigDecimal("21.00"), "VIAL"));

        assertEquals("L1", out.batchNumber());
        assertEquals("SN-1", out.serialNumber());
        assertEquals(EXPIRES, out.expirationDate());
    }

    @Test
    @DisplayName("an update replaces them, and \"\" removes a batch or serial number")
    void updateReplacesAndRemoves() throws Exception {
        when(repository.findById(id)).thenReturn(Optional.of(stored(ProductTraceability.of("L1", "SN-1", EXPIRES))));

        ProductOutputDTO out = update.execute(new UpdateProductInputDTO(id.value(), "Insulin 100IU", null,
                new BigDecimal("20.00"), "VIAL", "L2", "", EXPIRES.plusMonths(6)));

        assertEquals("L2", out.batchNumber());
        assertNull(out.serialNumber());
        assertEquals(EXPIRES.plusMonths(6), out.expirationDate());
    }

    @Test
    @DisplayName("an expired product cannot be reserved")
    void expiredCannotBeReserved() throws Exception {
        when(repository.findById(id)).thenReturn(Optional.of(stored(ProductTraceability.of("L1", null, EXPIRED))));
        ReserveProductStockUseCase reserve = new ReserveProductStockUseCase(repository, publisher,
                UnitOfWork.immediate(), TODAY);

        DomainException e = assertThrows(DomainException.class,
                () -> reserve.execute(new StockQuantityInputDTO(id.value(), 1)));

        assertEquals("Product " + id.value() + " is expired.", e.getMessage());
        verify(repository, never()).tryReserveStock(any(), anyInt());
    }

    @Test
    @DisplayName("an expired product is never available; one expiring later is")
    void availability() throws Exception {
        CheckProductAvailabilityUseCase check = new CheckProductAvailabilityUseCase(repository, TODAY);

        when(repository.findById(id)).thenReturn(Optional.of(stored(ProductTraceability.of(null, null, EXPIRED))));
        assertFalse(check.execute(new StockQuantityInputDTO(id.value(), 1)).available());

        when(repository.findById(id)).thenReturn(Optional.of(stored(ProductTraceability.of(null, null, EXPIRES))));
        assertTrue(check.execute(new StockQuantityInputDTO(id.value(), 1)).available());
    }
}
