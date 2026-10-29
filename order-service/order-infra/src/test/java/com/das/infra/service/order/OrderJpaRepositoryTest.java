package com.das.infra.service.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test for order-infra's JPA mapping — this module had no automated tests at
 * all until now. Verifies OrderEntity actually persists and round-trips through a
 * real (in-memory) database, which is the minimum needed to catch a broken
 * {@code @Entity}/{@code @Column} annotation before it surfaces in production.
 */
@DataJpaTest
class OrderJpaRepositoryTest {

    @Autowired
    private OrderJpaRepository repository;

    @Test
    void savesAndReloadsAnOrder() {
        String id = UUID.randomUUID().toString();
        OrderEntity entity = new OrderEntity();
        entity.setId(id);
        entity.setMedicalSalesRepId(UUID.randomUUID().toString());
        entity.setStatus("PENDING_APPROVAL");
        entity.setCreatedAt(Instant.now());

        repository.save(entity);

        Optional<OrderEntity> found = repository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void findByMedicalSalesRepId_returnsOnlyThatRepsOrders() {
        String repId = UUID.randomUUID().toString();
        OrderEntity mine = new OrderEntity();
        mine.setId(UUID.randomUUID().toString());
        mine.setMedicalSalesRepId(repId);
        mine.setStatus("PENDING_APPROVAL");
        mine.setCreatedAt(Instant.now());
        repository.save(mine);

        OrderEntity someoneElses = new OrderEntity();
        someoneElses.setId(UUID.randomUUID().toString());
        someoneElses.setMedicalSalesRepId(UUID.randomUUID().toString());
        someoneElses.setStatus("PENDING_APPROVAL");
        someoneElses.setCreatedAt(Instant.now());
        repository.save(someoneElses);

        var page = repository.findByMedicalSalesRepId(repId, org.springframework.data.domain.PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(OrderEntity::getId).containsExactly(mine.getId());
    }
}
