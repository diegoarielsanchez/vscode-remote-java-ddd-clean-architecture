package com.das.cleanddd.domain.catalog.usecases.services;

import java.math.BigDecimal;

import com.das.cleanddd.domain.catalog.entities.IProductRepository;
import com.das.cleanddd.domain.catalog.entities.Product;
import com.das.cleanddd.domain.catalog.ports.IProductEventPublisher;
import com.das.cleanddd.domain.catalog.usecases.dtos.CreateProductInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.ProductMapper;
import com.das.cleanddd.domain.catalog.usecases.dtos.ProductOutputDTO;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateProductUseCase")
class CreateProductUseCaseTest {

    @Mock private IProductRepository repository;
    @Mock private IProductEventPublisher publisher;

    private ProductMapper mapper;
    private CreateProductUseCase useCase;

    @BeforeEach
    void setUp() {
        mapper = new ProductMapper();
        useCase = new CreateProductUseCase(repository, mapper, publisher);
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("should create and persist a new Product, inactive by default")
        void shouldCreateAndPersist() throws DomainException {
            CreateProductInputDTO input =
                    new CreateProductInputDTO("Amoxicillin 500mg", "Antibiotic", new BigDecimal("12.50"), "BOX", 100);

            ProductOutputDTO output = useCase.execute(input);

            assertNotNull(output.id());
            assertEquals("Amoxicillin 500mg", output.name());
            assertEquals(0, new BigDecimal("12.50").compareTo(output.price()));
            assertEquals("BOX", output.unit());
            assertEquals(100, output.stock());
            assertFalse(output.active()); // newly created → inactive

            verify(repository, times(1)).save(any(Product.class));
            verify(publisher, times(1)).publish(any());
        }

        @Test
        @DisplayName("should default initialStock to zero when null")
        void shouldDefaultStockToZeroWhenNull() throws DomainException {
            CreateProductInputDTO input =
                    new CreateProductInputDTO("Ibuprofen 400mg", null, new BigDecimal("5.00"), "VIAL", null);

            ProductOutputDTO output = useCase.execute(input);

            assertEquals(0, output.stock());
        }
    }

    @Nested
    @DisplayName("Input validation")
    class InputValidation {

        @Test
        @DisplayName("should throw DomainException when input is null")
        void shouldThrowWhenInputNull() {
            assertThrows(DomainException.class, () -> useCase.execute(null));
        }

        @Test
        @DisplayName("should throw DomainException when name is blank")
        void shouldThrowWhenNameBlank() {
            CreateProductInputDTO input = new CreateProductInputDTO("", null, new BigDecimal("1.00"), "UNIT", 1);
            assertThrows(DomainException.class, () -> useCase.execute(input));
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("should throw DomainException when price is negative")
        void shouldThrowWhenPriceNegative() {
            CreateProductInputDTO input =
                    new CreateProductInputDTO("Aspirin", null, new BigDecimal("-1.00"), "UNIT", 1);
            assertThrows(DomainException.class, () -> useCase.execute(input));
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("should throw DomainException when unit is blank")
        void shouldThrowWhenUnitBlank() {
            CreateProductInputDTO input = new CreateProductInputDTO("Aspirin", null, new BigDecimal("1.00"), "", 1);
            assertThrows(DomainException.class, () -> useCase.execute(input));
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("should not publish event when creation fails")
        void shouldNotPublishWhenCreationFails() {
            assertThrows(DomainException.class,
                    () -> useCase.execute(new CreateProductInputDTO("", null, new BigDecimal("1.00"), "UNIT", 1)));
            verify(publisher, never()).publish(any());
        }
    }
}
