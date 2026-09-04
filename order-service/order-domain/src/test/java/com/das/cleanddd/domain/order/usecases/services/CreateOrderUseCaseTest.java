package com.das.cleanddd.domain.order.usecases.services;

import java.math.BigDecimal;
import java.util.List;

import com.das.cleanddd.domain.order.entities.IOrderRepository;
import com.das.cleanddd.domain.order.entities.Order;
import com.das.cleanddd.domain.order.entities.OrderStatus;
import com.das.cleanddd.domain.order.ports.IMedicalSalesRepValidator;
import com.das.cleanddd.domain.order.ports.IOrderEventPublisher;
import com.das.cleanddd.domain.order.ports.IProductStockPort;
import com.das.cleanddd.domain.order.ports.IProductStockPort.StockReservationResult;
import com.das.cleanddd.domain.order.usecases.dtos.CreateOrderInputDTO;
import com.das.cleanddd.domain.order.usecases.dtos.OrderLineInputDTO;
import com.das.cleanddd.domain.order.usecases.dtos.OrderMapper;
import com.das.cleanddd.domain.order.usecases.dtos.OrderOutputDTO;
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
@DisplayName("CreateOrderUseCase")
class CreateOrderUseCaseTest {

    @Mock private IOrderRepository repository;
    @Mock private IOrderEventPublisher publisher;
    @Mock private IMedicalSalesRepValidator medicalSalesRepValidator;
    @Mock private IProductStockPort productStockPort;

    private OrderMapper mapper;
    private CreateOrderUseCase useCase;

    private final String msrId = java.util.UUID.randomUUID().toString();
    private final String productA = java.util.UUID.randomUUID().toString();
    private final String productB = java.util.UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        mapper = new OrderMapper();
        useCase = new CreateOrderUseCase(repository, mapper, publisher, medicalSalesRepValidator, productStockPort);
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("should reserve stock, create the order PENDING_APPROVAL, and publish events")
        void shouldCreateOrder() throws DomainException {
            when(medicalSalesRepValidator.existsAndActive(msrId)).thenReturn(true);
            when(productStockPort.reserve(eq(productA), eq(5)))
                    .thenReturn(new StockReservationResult(true, 95, new BigDecimal("12.50"), "Amoxicillin 500mg"));

            CreateOrderInputDTO input = new CreateOrderInputDTO(msrId, List.of(new OrderLineInputDTO(productA, 5)));
            OrderOutputDTO output = useCase.execute(input);

            assertEquals(OrderStatus.PENDING_APPROVAL.name(), output.status());
            assertEquals(1, output.lines().size());
            assertEquals(0, new BigDecimal("62.50").compareTo(output.totalAmount()));

            verify(repository, times(1)).save(any(Order.class));
            verify(publisher, atLeastOnce()).publish(any());
            verify(productStockPort, never()).release(any(), anyInt());
        }
    }

    @Nested
    @DisplayName("MSR validation")
    class MsrValidation {

        @Test
        @DisplayName("should throw and never call product-catalog when the MSR is not active")
        void shouldThrowWhenMsrNotActive() {
            when(medicalSalesRepValidator.existsAndActive(msrId)).thenReturn(false);

            CreateOrderInputDTO input = new CreateOrderInputDTO(msrId, List.of(new OrderLineInputDTO(productA, 5)));

            assertThrows(DomainException.class, () -> useCase.execute(input));
            verifyNoInteractions(productStockPort);
            verify(repository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Compensation on partial failure")
    class Compensation {

        @Test
        @DisplayName("should release every already-reserved line when a later line fails, and never create the order")
        void shouldCompensateOnPartialFailure() throws DomainException {
            when(medicalSalesRepValidator.existsAndActive(msrId)).thenReturn(true);
            when(productStockPort.reserve(eq(productA), eq(5)))
                    .thenReturn(new StockReservationResult(true, 95, new BigDecimal("12.50"), "Amoxicillin 500mg"));
            when(productStockPort.reserve(eq(productB), eq(3)))
                    .thenReturn(new StockReservationResult(false, 0, null, null));

            CreateOrderInputDTO input = new CreateOrderInputDTO(msrId,
                    List.of(new OrderLineInputDTO(productA, 5), new OrderLineInputDTO(productB, 3)));

            assertThrows(DomainException.class, () -> useCase.execute(input));

            verify(productStockPort, times(1)).release(eq(productA), eq(5));
            verify(repository, never()).save(any());
            verify(publisher, never()).publish(any());
        }
    }
}
