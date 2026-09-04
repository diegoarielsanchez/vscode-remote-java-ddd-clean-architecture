package com.das.cleanddd.domain.catalog.usecases.services;

import java.math.BigDecimal;
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
import com.das.cleanddd.domain.catalog.ports.IProductEventPublisher;
import com.das.cleanddd.domain.catalog.usecases.dtos.ReserveStockOutputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.StockQuantityInputDTO;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReserveProductStockUseCase")
class ReserveProductStockUseCaseTest {

    @Mock private IProductRepository repository;
    @Mock private IProductEventPublisher publisher;

    private ReserveProductStockUseCase useCase;
    private ProductId productId;

    @BeforeEach
    void setUp() {
        useCase = new ReserveProductStockUseCase(repository, publisher);
        productId = ProductId.random();
    }

    private Product product(int stock) throws BusinessValidationException {
        return new Product(productId, new ProductName("Amoxicillin 500mg"), new ProductDescription("Antibiotic"),
                new ProductPrice(new BigDecimal("12.50")), new ProductUnit("BOX"), new ProductStock(stock),
                new ProductActive(true));
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("should reserve stock atomically and return the remaining stock and unit price")
        void shouldReserveStock() throws Exception {
            when(repository.findById(eq(productId)))
                    .thenReturn(Optional.of(product(100)))
                    .thenReturn(Optional.of(product(90)));
            when(repository.tryReserveStock(eq(productId), eq(10))).thenReturn(true);

            ReserveStockOutputDTO output = useCase.execute(new StockQuantityInputDTO(productId.value(), 10));

            assertTrue(output.reserved());
            assertEquals(90, output.remainingStock());
            assertEquals(0, new BigDecimal("12.50").compareTo(output.unitPrice()));
            verify(publisher, times(1)).publish(any());
        }
    }

    @Nested
    @DisplayName("Insufficient stock")
    class InsufficientStock {

        @Test
        @DisplayName("should throw DomainException and not publish when reservation fails")
        void shouldThrowWhenInsufficientStock() throws BusinessValidationException {
            when(repository.findById(eq(productId))).thenReturn(Optional.of(product(5)));
            when(repository.tryReserveStock(eq(productId), anyInt())).thenReturn(false);

            assertThrows(DomainException.class,
                    () -> useCase.execute(new StockQuantityInputDTO(productId.value(), 10)));

            verify(publisher, never()).publish(any());
        }
    }

    @Nested
    @DisplayName("Input validation")
    class InputValidation {

        @Test
        @DisplayName("should throw DomainException when quantity is not positive")
        void shouldThrowWhenQuantityNotPositive() {
            assertThrows(DomainException.class,
                    () -> useCase.execute(new StockQuantityInputDTO(productId.value(), 0)));
            verifyNoInteractions(repository, publisher);
        }

        @Test
        @DisplayName("should throw DomainException when product does not exist")
        void shouldThrowWhenProductNotFound() {
            when(repository.findById(eq(productId))).thenReturn(Optional.empty());
            assertThrows(DomainException.class,
                    () -> useCase.execute(new StockQuantityInputDTO(productId.value(), 1)));
            verify(repository, never()).tryReserveStock(any(), anyInt());
        }
    }
}
