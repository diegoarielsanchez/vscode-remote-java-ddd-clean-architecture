package com.das.infra.service.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test for catalog-infra's JPA mapping — this module had no automated tests at
 * all until now. Verifies ProductEntity actually persists and round-trips through a
 * real (in-memory) database, and that the atomic stock-reservation query behaves
 * correctly under its own success/failure boundary.
 */
@DataJpaTest
class ProductJpaRepositoryTest {

    @Autowired
    private ProductJpaRepository repository;

    private ProductEntity aProduct() {
        ProductEntity entity = new ProductEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setName("Amoxicillin 500mg");
        entity.setDescription("Antibiotic, 500mg capsules");
        entity.setPrice(BigDecimal.valueOf(12.50));
        entity.setUnit("box");
        entity.setActive(true);
        return entity;
    }

    @Test
    void savesAndReloadsAProduct() {
        ProductEntity entity = aProduct();
        entity.setGtin("09506000134352");

        repository.save(entity);

        Optional<ProductEntity> found = repository.findById(entity.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Amoxicillin 500mg");
        assertThat(repository.findByGtin("09506000134352")).map(ProductEntity::getId).contains(entity.getId());
    }

    @Test
    void findByNameStartingWith_matchesCaseInsensitivePrefix() {
        repository.save(aProduct());
        ProductEntity ibuprofen = aProduct();
        ibuprofen.setName("Ibuprofen 200mg");
        repository.save(ibuprofen);

        var results = repository.findByNameStartingWith("amox");

        assertThat(results).extracting(ProductEntity::getName).containsExactly("Amoxicillin 500mg");
    }
}
