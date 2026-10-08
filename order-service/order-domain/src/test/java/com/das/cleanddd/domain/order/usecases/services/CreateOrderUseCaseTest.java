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
import com.das.cleanddd.domain.shared.UnitOfWork;
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
        useCase = new CreateOrderUseCase(repository, mapper, publisher, medicalSalesRepValidator, productStockPort, UnitOfWork.immediate());
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
    @DisplayName("Unit of work (transactional outbox)")
    class UnitOfWorkBoundary {

        @Test
        @DisplayName("should save and publish inside the unit of work, but reserve stock outside it")
        void shouldSaveAndPublishInsideTheUnitOfWork() throws DomainException {
            java.util.List<String> calls = new java.util.ArrayList<>();
            boolean[] inside = {false};
            UnitOfWork recording = new UnitOfWork() {
                @Override
                public <T> T execute(Work<T> work) throws DomainException {
                    inside[0] = true;
                    try {
                        return work.run();
                    } finally {
                        inside[0] = false;
                    }
                }
            };
            when(medicalSalesRepValidator.existsAndActive(msrId)).thenReturn(true);
            when(productStockPort.reserve(eq(productA), eq(5))).thenAnswer(inv -> {
                calls.add("reserve:" + inside[0]);
                return new StockReservationResult(true, 95, new BigDecimal("12.50"), "Amoxicillin 500mg");
            });
            doAnswer(inv -> { calls.add("save:" + inside[0]); return null; }).when(repository).save(any());
            doAnswer(inv -> { calls.add("publish:" + inside[0]); return null; }).when(publisher).publish(any());

            new CreateOrderUseCase(repository, mapper, publisher, medicalSalesRepValidator, productStockPort, recording)
                    .execute(new CreateOrderInputDTO(msrId, List.of(new OrderLineInputDTO(productA, 5))));

            assertEquals("reserve:false", calls.get(0), "no transaction is held open across the REST reservation");
            assertEquals("save:true", calls.get(1));
            assertTrue(calls.subList(2, calls.size()).stream().allMatch("publish:true"::equals));
        }

        @Test
        @DisplayName("should release every reservation when saving the order fails")
        void shouldReleaseReservationsWhenSaveFails() {
            when(medicalSalesRepValidator.existsAndActive(msrId)).thenReturn(true);
            when(productStockPort.reserve(eq(productA), eq(5)))
                    .thenReturn(new StockReservationResult(true, 95, new BigDecimal("12.50"), "Amoxicillin 500mg"));
            when(productStockPort.reserve(eq(productB), eq(2)))
                    .thenReturn(new StockReservationResult(true, 8, new BigDecimal("3.00"), "Ibuprofen 400mg"));
            doThrow(new IllegalStateException("database unavailable")).when(repository).save(any());

            CreateOrderInputDTO input = new CreateOrderInputDTO(msrId,
                    List.of(new OrderLineInputDTO(productA, 5), new OrderLineInputDTO(productB, 2)));

            assertThrows(IllegalStateException.class, () -> useCase.execute(input));
            verify(productStockPort).release(productA, 5);
            verify(productStockPort).release(productB, 2);
            verify(publisher, never()).publish(any());
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
