package com.das.cleanddd.domain.order.usecases.services;

import java.util.List;

import com.das.cleanddd.domain.order.entities.IOrderRepository;
import com.das.cleanddd.domain.order.entities.Order;
import com.das.cleanddd.domain.order.entities.OrderStatus;
import com.das.cleanddd.domain.order.events.OrderCreatedEvent;
import com.das.cleanddd.domain.order.ports.IMedicalSalesRepValidator;
import com.das.cleanddd.domain.order.ports.IOrderEventPublisher;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateOrderUseCase")
class CreateOrderUseCaseTest {

    @Mock private IOrderRepository repository;
    @Mock private IOrderEventPublisher publisher;
    @Mock private IMedicalSalesRepValidator medicalSalesRepValidator;

    private OrderMapper mapper;
    private CreateOrderUseCase useCase;

    private final String msrId = java.util.UUID.randomUUID().toString();
    private final String productA = java.util.UUID.randomUUID().toString();
    private final String productB = java.util.UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        mapper = new OrderMapper();
        useCase = new CreateOrderUseCase(repository, mapper, publisher, medicalSalesRepValidator, UnitOfWork.immediate());
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("should save the order AWAITING_STOCK, unpriced, and publish only order.created with the lines")
        void shouldCreateOrder() throws DomainException {
            when(medicalSalesRepValidator.existsAndActive(msrId)).thenReturn(true);

            OrderOutputDTO output = useCase.execute(new CreateOrderInputDTO(msrId,
                    List.of(new OrderLineInputDTO(productA, 5), new OrderLineInputDTO(productB, 2))));

            assertEquals(OrderStatus.AWAITING_STOCK.name(), output.status());
            assertNull(output.totalAmount());
            assertEquals(2, output.lines().size());
            assertNull(output.lines().get(0).unitPrice());
            verify(repository).save(any(Order.class));
            ArgumentCaptor<OrderCreatedEvent> event = ArgumentCaptor.forClass(OrderCreatedEvent.class);
            verify(publisher).publish(event.capture());
            assertEquals(output.id(), event.getValue().id());
            assertEquals(List.of(new OrderCreatedEvent.Line(productA, 5), new OrderCreatedEvent.Line(productB, 2)),
                    event.getValue().lines());
        }
    }

    @Nested
    @DisplayName("Unit of work (transactional outbox)")
    class UnitOfWorkBoundary {

        @Test
        @DisplayName("should save and publish inside the unit of work")
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
            doAnswer(inv -> { calls.add("save:" + inside[0]); return null; }).when(repository).save(any());
            doAnswer(inv -> { calls.add("publish:" + inside[0]); return null; }).when(publisher).publish(any());

            new CreateOrderUseCase(repository, mapper, publisher, medicalSalesRepValidator, recording)
                    .execute(new CreateOrderInputDTO(msrId, List.of(new OrderLineInputDTO(productA, 5))));

            assertEquals(List.of("save:true", "publish:true"), calls);
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("should throw and save nothing when the MSR is not active")
        void shouldThrowWhenMsrNotActive() {
            when(medicalSalesRepValidator.existsAndActive(msrId)).thenReturn(false);

            CreateOrderInputDTO input = new CreateOrderInputDTO(msrId, List.of(new OrderLineInputDTO(productA, 5)));

            assertThrows(DomainException.class, () -> useCase.execute(input));
            verify(repository, never()).save(any());
            verifyNoInteractions(publisher);
        }

        @Test
        @DisplayName("should refuse a non-positive quantity")
        void shouldRefuseZeroQuantity() {
            when(medicalSalesRepValidator.existsAndActive(msrId)).thenReturn(true);

            CreateOrderInputDTO input = new CreateOrderInputDTO(msrId, List.of(new OrderLineInputDTO(productA, 0)));

            assertThrows(DomainException.class, () -> useCase.execute(input));
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("should refuse an order without lines")
        void shouldRefuseNoLines() {
            assertThrows(DomainException.class, () -> useCase.execute(new CreateOrderInputDTO(msrId, List.of())));
            verifyNoInteractions(medicalSalesRepValidator, repository, publisher);
        }
    }
}
