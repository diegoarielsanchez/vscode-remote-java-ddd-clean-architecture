package com.das.infra.service.order;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.das.cleanddd.domain.order.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.order.entities.Order;
import com.das.cleanddd.domain.order.entities.OrderLine;
import com.das.cleanddd.domain.order.entities.OrderLineQuantity;
import com.das.cleanddd.domain.order.entities.OrderLineUnitPrice;
import com.das.cleanddd.domain.order.entities.OrderStatus;
import com.das.cleanddd.domain.order.entities.ProductId;
import com.das.cleanddd.domain.order.entities.ReservedLinePrice;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;

/** The saga stores an order unpriced first, then again priced — both must round-trip. */
@DataJpaTest
@Import(SQLOrderRepository.class)
class SQLOrderRepositoryTest {

    @Autowired private SQLOrderRepository repository;
    @Autowired private EntityManager entityManager;
    @Autowired private OrderJpaRepository orderRows;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void readsAnOrderWithItsLinesOutsideACallerTransaction() throws Exception {
        // As GET /orders/{id} does: no surrounding transaction and open-in-view is off.
        ProductId product = new ProductId(UUID.randomUUID().toString());
        Order created = Order.create(new MedicalSalesRepId(UUID.randomUUID().toString()),
                List.of(OrderLine.unpriced(product, new OrderLineQuantity(3))));
        repository.save(created);
        try {
            assertThat(repository.findById(created.id()).orElseThrow().lines()).hasSize(1);
            assertThat(repository.findByMedicalSalesRepId(created.medicalSalesRepId(), 1, 10)).hasSize(1);
            assertThat(repository.searchAll()).extracting(Order::id).contains(created.id());
        } finally {
            orderRows.deleteById(created.id().value());
        }
    }

    @Test
    void storesAnUnpricedOrderAndThenItsPricedLines() throws Exception {
        ProductId product = new ProductId(UUID.randomUUID().toString());
        Order created = Order.create(new MedicalSalesRepId(UUID.randomUUID().toString()),
                List.of(OrderLine.unpriced(product, new OrderLineQuantity(3))));
        repository.save(created);
        entityManager.flush();
        entityManager.clear();

        Order awaiting = repository.findById(created.id()).orElseThrow();
        assertThat(awaiting.status()).isEqualTo(OrderStatus.AWAITING_STOCK);
        assertThat(awaiting.totalAmount()).isNull();
        assertThat(awaiting.lines().get(0).unitPrice()).isNull();

        repository.save(awaiting.confirmStock(List.of(
                new ReservedLinePrice(product, "Amoxicillin 500mg", new OrderLineUnitPrice(new BigDecimal("2.50"))))));
        entityManager.flush();
        entityManager.clear();

        Order pending = repository.findById(created.id()).orElseThrow();
        assertThat(pending.status()).isEqualTo(OrderStatus.PENDING_APPROVAL);
        assertThat(pending.lines()).hasSize(1);
        assertThat(pending.lines().get(0).id()).isEqualTo(created.lines().get(0).id());
        assertThat(pending.lines().get(0).productNameSnapshot()).isEqualTo("Amoxicillin 500mg");
        assertThat(pending.totalAmount()).isEqualByComparingTo("7.50");
    }
}
