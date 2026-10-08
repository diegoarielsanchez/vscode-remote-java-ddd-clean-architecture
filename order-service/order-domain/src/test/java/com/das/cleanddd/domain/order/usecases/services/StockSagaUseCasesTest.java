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
import com.das.cleanddd.domain.order.events.OrderStockRejectedEvent;
import com.das.cleanddd.domain.order.events.OrderSubmittedForApprovalEvent;
import com.das.cleanddd.domain.order.ports.IOrderEventPublisher;
import com.das.cleanddd.domain.order.usecases.dtos.ConfirmOrderStockInputDTO;
import com.das.cleanddd.domain.order.usecases.dtos.ConfirmOrderStockInputDTO.ReservedLine;
import com.das.cleanddd.domain.order.usecases.dtos.OrderMapper;
import com.das.cleanddd.domain.order.usecases.dtos.OrderOutputDTO;
import com.das.cleanddd.domain.order.usecases.dtos.RejectOrderForStockInputDTO;
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
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Stock saga use cases (confirm / reject for stock)")
class StockSagaUseCasesTest {

    @Mock private IOrderRepository repository;
    @Mock private IOrderEventPublisher publisher;

    private ConfirmOrderStockUseCase confirm;
    private RejectOrderForStockUseCase reject;
    private final String product = UUID.randomUUID().toString();
    private Order awaiting;

    @BeforeEach
    void setUp() throws Exception {
        confirm = new ConfirmOrderStockUseCase(repository, new OrderMapper(), publisher, UnitOfWork.immediate());
        reject = new RejectOrderForStockUseCase(repository, new OrderMapper(), publisher, UnitOfWork.immediate());
        awaiting = Order.create(new MedicalSalesRepId(UUID.randomUUID().toString()),
                List.of(OrderLine.unpriced(new ProductId(product), new OrderLineQuantity(4))));
        awaiting.pullDomainEvents();
    }

    private ConfirmOrderStockInputDTO confirmation() {
        return new ConfirmOrderStockInputDTO(awaiting.id().value(),
                List.of(new ReservedLine(product, "Amoxicillin 500mg", new BigDecimal("2.50"))));
    }

    @Test
    @DisplayName("confirm prices the order, saves it PENDING_APPROVAL and publishes order.submitted-for-approval")
    void confirmMovesToPendingApproval() throws DomainException {
        when(repository.findById(any(OrderId.class))).thenReturn(Optional.of(awaiting));

        OrderOutputDTO out = confirm.execute(confirmation());

        assertEquals(OrderStatus.PENDING_APPROVAL.name(), out.status());
        assertEquals(0, new BigDecimal("10.00").compareTo(out.totalAmount()));
        verify(repository).save(any(Order.class));
        verify(publisher).publish(isA(OrderSubmittedForApprovalEvent.class));
    }

    @Test
    @DisplayName("confirm is a no-op once the order has left AWAITING_STOCK (redelivery)")
    void confirmIsIdempotent() throws Exception {
        Order rejected = awaiting.rejectForStock("no stock");
        rejected.pullDomainEvents();
        when(repository.findById(any(OrderId.class))).thenReturn(Optional.of(rejected));

        OrderOutputDTO out = confirm.execute(confirmation());

        assertEquals(OrderStatus.STOCK_REJECTED.name(), out.status());
        verify(repository, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("confirm fails for an unknown order")
    void confirmUnknownOrder() {
        when(repository.findById(any(OrderId.class))).thenReturn(Optional.empty());
        assertThrows(DomainException.class, () -> confirm.execute(confirmation()));
    }

    @Test
    @DisplayName("confirm fails when the reservation misses a product")
    void confirmMissingProduct() {
        when(repository.findById(any(OrderId.class))).thenReturn(Optional.of(awaiting));
        var input = new ConfirmOrderStockInputDTO(awaiting.id().value(),
                List.of(new ReservedLine(UUID.randomUUID().toString(), "Other", BigDecimal.ONE)));
        assertThrows(DomainException.class, () -> confirm.execute(input));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("reject saves STOCK_REJECTED and publishes order.stock-rejected")
    void rejectMovesToStockRejected() throws DomainException {
        when(repository.findById(any(OrderId.class))).thenReturn(Optional.of(awaiting));

        OrderOutputDTO out = reject.execute(new RejectOrderForStockInputDTO(awaiting.id().value(), "Insufficient stock"));

        assertEquals(OrderStatus.STOCK_REJECTED.name(), out.status());
        assertEquals("Insufficient stock", out.rejectionReason());
        verify(publisher).publish(isA(OrderStockRejectedEvent.class));
    }

    @Test
    @DisplayName("reject is a no-op once the order has left AWAITING_STOCK")
    void rejectIsIdempotent() throws Exception {
        Order pending = awaiting.confirmStock(List.of(new ReservedLinePrice(
                new ProductId(product), "Amoxicillin 500mg",
                new OrderLineUnitPrice(new BigDecimal("2.50")))));
        pending.pullDomainEvents();
        when(repository.findById(any(OrderId.class))).thenReturn(Optional.of(pending));

        OrderOutputDTO out = reject.execute(new RejectOrderForStockInputDTO(awaiting.id().value(), "late"));

        assertEquals(OrderStatus.PENDING_APPROVAL.name(), out.status());
        verify(repository, never()).save(any());
        verifyNoInteractions(publisher);
    }
}
