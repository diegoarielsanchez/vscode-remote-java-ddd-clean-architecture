package com.das.cleanddd.domain.order.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.das.cleanddd.domain.order.events.OrderDeliveredEvent;
import com.das.cleanddd.domain.order.events.OrderDeliveryQuarantinedEvent;
import com.das.cleanddd.domain.order.events.OrderDeliveryRejectedEvent;
import com.das.cleanddd.domain.order.events.OrderDeliveryReleasedEvent;
import com.das.cleanddd.domain.order.events.OrderDomainEvent;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;
import com.das.cleanddd.domain.shared.storage.ConditionReadings;
import com.das.cleanddd.domain.shared.storage.StorageCondition;

/** GDP: transport must keep the products' storage conditions; a break quarantines the delivery for Quality. */
class OrderTransportTest {

    private static final StorageCondition FRIDGE = new StorageCondition(new BigDecimal("2"), new BigDecimal("8"), null, false);
    private static final Instant ARRIVED = Instant.parse("2026-10-27T10:00:00Z");

    private final ProductId insulin = new ProductId(UUID.randomUUID().toString());
    private final ProductId brochure = new ProductId(UUID.randomUUID().toString());

    private Order approved(StorageCondition insulinCondition) throws BusinessValidationException {
        Order order = Order.create(new MedicalSalesRepId(UUID.randomUUID().toString()), List.of(
                        OrderLine.unpriced(insulin, new OrderLineQuantity(2)),
                        OrderLine.unpriced(brochure, new OrderLineQuantity(10))))
                .confirmStock(List.of(
                        new ReservedLinePrice(insulin, "Insulin 100IU", new OrderLineUnitPrice(new BigDecimal("20.00")),
                                insulinCondition),
                        new ReservedLinePrice(brochure, "Brochure", new OrderLineUnitPrice(BigDecimal.ZERO))))
                .approve("agent");
        order.pullDomainEvents();
        return order;
    }

    private static ConditionReadings max(String celsius) {
        return new ConditionReadings(null, new BigDecimal(celsius), null, null);
    }

    @Test
    @DisplayName("the lines keep the conditions the catalog reported")
    void linesCarryTheConditions() throws BusinessValidationException {
        Order order = approved(FRIDGE);

        assertEquals(FRIDGE, order.lines().get(0).storageCondition());
        assertNull(order.lines().get(1).storageCondition());
        assertTrue(order.requiresTransportCheck());
    }

    @Test
    @DisplayName("an order with conditions cannot be delivered without a transport report")
    void reportRequired() throws BusinessValidationException {
        Order order = approved(FRIDGE);

        BusinessValidationException e = assertThrows(BusinessValidationException.class,
                () -> order.markDelivered(ARRIVED));
        assertTrue(e.getMessage().contains("Insulin 100IU 2.0 to 8.0 °C"), e.getMessage());
    }

    @Test
    @DisplayName("conditions kept: DELIVERED, as before")
    void kept() throws BusinessValidationException {
        Order delivered = approved(FRIDGE).markDelivered(ARRIVED, DeliveryCheck.reported(true, max("7.5"), null));

        assertEquals(OrderStatus.DELIVERED, delivered.status());
        assertTrue(delivered.deliveryCheck().kept());
        assertInstanceOf(OrderDeliveredEvent.class, delivered.pullDomainEvents().get(0));
    }

    @Test
    @DisplayName("a reading outside the conditions quarantines the delivery even when the receiver says they were kept")
    void readingBreaks() throws BusinessValidationException {
        Order quarantined = approved(FRIDGE).markDelivered(ARRIVED,
                DeliveryCheck.reported(true, max("11.0"), "Box was warm on arrival"));

        assertEquals(OrderStatus.DELIVERY_QUARANTINED, quarantined.status());
        assertEquals(ARRIVED, quarantined.deliveredAt(), "the goods did arrive");
        OrderDeliveryQuarantinedEvent event = assertInstanceOf(OrderDeliveryQuarantinedEvent.class,
                quarantined.pullDomainEvents().get(0));
        assertEquals(List.of("Insulin 100IU: Highest temperature 11.0 °C is above 8.0 °C."), event.breaches());
        assertEquals("Box was warm on arrival", event.note());
        assertSame(quarantined, quarantined.markDelivered(ARRIVED, DeliveryCheck.reported(true, null, null)),
                "a repeated confirmation changes nothing");
    }

    @Test
    @DisplayName("the receiver's own report of a break also quarantines it")
    void receiverReportsBreak() throws BusinessValidationException {
        Order quarantined = approved(FRIDGE).markDelivered(ARRIVED, DeliveryCheck.reported(false, null, "Cool pack missing"));

        assertEquals(OrderStatus.DELIVERY_QUARANTINED, quarantined.status());
        assertTrue(quarantined.deliveryCheck().breaches().isEmpty());
    }

    @Test
    @DisplayName("Quality releases a quarantined delivery: DELIVERED, so the catalog fulfils the reservation")
    void release() throws BusinessValidationException {
        Order quarantined = approved(FRIDGE).markDelivered(ARRIVED, DeliveryCheck.reported(true, max("8.4"), null));
        quarantined.pullDomainEvents();

        Order released = quarantined.releaseDelivery("qa.admin", "Within the stability budget", ARRIVED.plusSeconds(3600));

        assertEquals(OrderStatus.DELIVERED, released.status());
        assertEquals("qa.admin", released.deliveryDecision().decidedBy());
        List<OrderDomainEvent> events = released.pullDomainEvents();
        assertInstanceOf(OrderDeliveryReleasedEvent.class, events.get(0));
        assertInstanceOf(OrderDeliveredEvent.class, events.get(1));
    }

    @Test
    @DisplayName("or rejects it, with a reason; only a quarantined delivery awaits a decision")
    void reject() throws BusinessValidationException {
        Order quarantined = approved(FRIDGE).markDelivered(ARRIVED, DeliveryCheck.reported(false, null, null));
        quarantined.pullDomainEvents();

        assertThrows(BusinessValidationException.class, () -> quarantined.rejectDelivery("qa.admin", " ", null));
        Order rejected = quarantined.rejectDelivery("qa.admin", "No stability data for 3 h at 25 °C", null);

        assertEquals(OrderStatus.DELIVERY_REJECTED, rejected.status());
        assertInstanceOf(OrderDeliveryRejectedEvent.class, rejected.pullDomainEvents().get(0));
        assertThrows(BusinessValidationException.class, () -> rejected.releaseDelivery("qa.admin", "Changed my mind", null));
        assertThrows(BusinessValidationException.class, () -> approved(FRIDGE).releaseDelivery("qa.admin", "Early", null));
    }

    @Test
    @DisplayName("an order without conditions is delivered as before; a report, if given, is kept")
    void noConditions() throws BusinessValidationException {
        assertEquals(OrderStatus.DELIVERED, approved(null).markDelivered(ARRIVED).status());

        Order reported = approved(null).markDelivered(ARRIVED, DeliveryCheck.reported(true, max("30"), null));
        assertEquals(OrderStatus.DELIVERED, reported.status(), "nothing to breach");
    }
}
