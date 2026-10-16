package com.das.cleanddd.domain.order.usecases.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.das.cleanddd.domain.order.entities.IOrderRepository;
import com.das.cleanddd.domain.order.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.order.entities.Order;
import com.das.cleanddd.domain.order.entities.OrderId;
import com.das.cleanddd.domain.order.entities.OrderLine;
import com.das.cleanddd.domain.order.entities.OrderLineQuantity;
import com.das.cleanddd.domain.order.entities.OrderLineUnitPrice;
import com.das.cleanddd.domain.order.entities.OrderStatus;
import com.das.cleanddd.domain.order.entities.ProductId;
import com.das.cleanddd.domain.order.entities.ReservedLinePrice;
import com.das.cleanddd.domain.order.ports.IOrderEventPublisher;
import com.das.cleanddd.domain.order.usecases.dtos.ConfirmOrderDeliveryInputDTO;
import com.das.cleanddd.domain.order.usecases.dtos.OrderMapper;
import com.das.cleanddd.domain.shared.UnitOfWork;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Confirm order delivery — ownership (OWASP A01)")
class ConfirmOrderDeliveryUseCaseTest {

    @Mock private IOrderRepository repository;
    @Mock private IOrderEventPublisher publisher;

    private ConfirmOrderDeliveryUseCase useCase;
    private final String ownerMsrId = UUID.randomUUID().toString();
    private Order approved;

    @BeforeEach
    void setUp() throws Exception {
        useCase = new ConfirmOrderDeliveryUseCase(repository, new OrderMapper(), publisher, UnitOfWork.immediate());
        ProductId product = new ProductId(UUID.randomUUID().toString());
        approved = Order.create(new MedicalSalesRepId(ownerMsrId),
                        List.of(OrderLine.unpriced(product, new OrderLineQuantity(2))))
                .confirmStock(List.of(new ReservedLinePrice(product, "Amoxicillin 500mg",
                        new OrderLineUnitPrice(new BigDecimal("2.50")))))
                .approve("agent");
        approved.pullDomainEvents();
        when(repository.findById(any(OrderId.class))).thenReturn(Optional.of(approved));
    }

    @Test
    @DisplayName("the MSR the order was placed for can confirm delivery")
    void ownerCanConfirm() throws DomainException {
        var out = useCase.execute(new ConfirmOrderDeliveryInputDTO(approved.id().value(), ownerMsrId));

        assertEquals(OrderStatus.DELIVERED.name(), out.status());
        verify(repository).save(any(Order.class));
    }

    @Test
    @DisplayName("a trusted caller (agent / delivery event) needs no MSR match")
    void trustedCallerCanConfirm() throws DomainException {
        var out = useCase.execute(new ConfirmOrderDeliveryInputDTO(approved.id().value(), null));

        assertEquals(OrderStatus.DELIVERED.name(), out.status());
    }

    @Test
    @DisplayName("another MSR gets the same answer as for a missing order, and nothing changes")
    void otherRepIsRefusedLikeMissingOrder() {
        var input = new ConfirmOrderDeliveryInputDTO(approved.id().value(), UUID.randomUUID().toString());

        DomainException ex = assertThrows(DomainException.class, () -> useCase.execute(input));

        assertEquals("Order not found.", ex.getMessage());
        verify(repository, never()).save(any());
        verifyNoInteractions(publisher);
    }
}
