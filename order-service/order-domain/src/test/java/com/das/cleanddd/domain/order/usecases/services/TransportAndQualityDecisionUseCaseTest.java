package com.das.cleanddd.domain.order.usecases.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.das.cleanddd.domain.order.entities.IOrderRepository;
import com.das.cleanddd.domain.order.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.order.entities.Order;
import com.das.cleanddd.domain.order.entities.OrderId;
import com.das.cleanddd.domain.order.entities.OrderLine;
import com.das.cleanddd.domain.order.entities.OrderLineQuantity;
import com.das.cleanddd.domain.order.entities.OrderLineUnitPrice;
import com.das.cleanddd.domain.order.entities.ProductId;
import com.das.cleanddd.domain.order.entities.ReservedLinePrice;
import com.das.cleanddd.domain.order.events.OrderDeliveryQuarantinedEvent;
import com.das.cleanddd.domain.order.ports.IOrderEventPublisher;
import com.das.cleanddd.domain.order.usecases.dtos.ConfirmOrderDeliveryInputDTO;
import com.das.cleanddd.domain.order.usecases.dtos.ConfirmOrderDeliveryInputDTO.TransportInputDTO;
import com.das.cleanddd.domain.order.usecases.dtos.DecideDeliveryInputDTO;
import com.das.cleanddd.domain.order.usecases.dtos.OrderMapper;
import com.das.cleanddd.domain.order.usecases.dtos.OrderOutputDTO;
import com.das.cleanddd.domain.shared.UnitOfWork;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import com.das.cleanddd.domain.shared.storage.StorageCondition;

@ExtendWith(MockitoExtension.class)
@DisplayName("Confirm delivery of a cold-chain order, and Quality's decision (GDP)")
class TransportAndQualityDecisionUseCaseTest {

    @Mock private IOrderRepository repository;
    @Mock private IOrderEventPublisher publisher;

    private ConfirmOrderDeliveryUseCase confirm;
    private DecideDeliveryUseCase release;
    private Order approved;

    @BeforeEach
    void setUp() throws Exception {
        confirm = new ConfirmOrderDeliveryUseCase(repository, new OrderMapper(), publisher, UnitOfWork.immediate());
        release = new DecideDeliveryUseCase(repository, new OrderMapper(), publisher, UnitOfWork.immediate(), true);
        ProductId insulin = new ProductId(UUID.randomUUID().toString());
        approved = Order.create(new MedicalSalesRepId(UUID.randomUUID().toString()),
                        List.of(OrderLine.unpriced(insulin, new OrderLineQuantity(2))))
                .confirmStock(List.of(new ReservedLinePrice(insulin, "Insulin 100IU",
                        new OrderLineUnitPrice(new BigDecimal("20.00")),
                        new StorageCondition(new BigDecimal("2"), new BigDecimal("8"), null, false))))
                .approve("agent");
        approved.pullDomainEvents();
        when(repository.findById(any(OrderId.class))).thenReturn(Optional.of(approved));
    }

    @Test
    @DisplayName("without a transport report, or without its conditionsMaintained, nothing is saved")
    void reportRequired() {
        String id = approved.id().value();
        assertThrows(DomainException.class, () -> confirm.execute(new ConfirmOrderDeliveryInputDTO(id, null)));
        assertThrows(DomainException.class, () -> confirm.execute(new ConfirmOrderDeliveryInputDTO(id, null,
                new TransportInputDTO(null, null, new BigDecimal("6"), null, null, null))));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("a logger reading above the range quarantines the delivery and says why")
    void breachQuarantines() throws DomainException {
        OrderOutputDTO out = confirm.execute(new ConfirmOrderDeliveryInputDTO(approved.id().value(), null,
                new TransportInputDTO(true, new BigDecimal("4"), new BigDecimal("9.5"), null, null, null)));

        assertEquals("DELIVERY_QUARANTINED", out.status());
        assertTrue(out.transportCheckRequired());
        assertEquals(List.of("Insulin 100IU: Highest temperature 9.5 °C is above 8.0 °C."), out.deliveryCheck().breaches());
        assertEquals("2.0 to 8.0 °C", out.lines().get(0).storageCondition().description());
        verify(publisher).publish(any(OrderDeliveryQuarantinedEvent.class));
    }

    @Test
    @DisplayName("Quality's release records who decided and why")
    void qualityReleases() throws DomainException {
        OrderOutputDTO quarantined = confirm.execute(new ConfirmOrderDeliveryInputDTO(approved.id().value(), null,
                new TransportInputDTO(false, null, null, null, null, "Cool pack missing")));
        assertEquals("DELIVERY_QUARANTINED", quarantined.status());
        Order stored = org.mockito.Mockito.mockingDetails(repository).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("save"))
                .map(i -> (Order) i.getArgument(0)).reduce((a, b) -> b).orElseThrow();
        when(repository.findById(any(OrderId.class))).thenReturn(Optional.of(stored));

        OrderOutputDTO out = release.execute(new DecideDeliveryInputDTO(approved.id().value(), "qa.admin",
                "Logger shows 2–8 °C throughout"));

        assertEquals("DELIVERED", out.status());
        assertEquals("qa.admin", out.deliveryCheck().decidedBy());
        assertEquals("Logger shows 2–8 °C throughout", out.deliveryCheck().decisionReason());
        assertEquals("Cool pack missing", out.deliveryCheck().note());
    }
}
