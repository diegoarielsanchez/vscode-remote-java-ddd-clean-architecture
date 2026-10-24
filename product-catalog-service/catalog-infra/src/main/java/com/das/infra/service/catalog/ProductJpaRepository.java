package com.das.infra.service.catalog;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, String> {

    @Query("SELECT e FROM ProductEntity e WHERE (:name = '' OR LOWER(e.name) LIKE LOWER(CONCAT(:name, '%')))")
    List<ProductEntity> findByNameStartingWith(@Param("name") String name);

    Optional<ProductEntity> findByGtin(String gtin);
}
