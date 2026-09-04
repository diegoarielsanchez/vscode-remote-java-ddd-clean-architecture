package com.das.infra.service.catalog;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, String> {

    @Query("SELECT e FROM ProductEntity e WHERE (:name = '' OR LOWER(e.name) LIKE LOWER(CONCAT(:name, '%')))")
    List<ProductEntity> findByNameStartingWith(@Param("name") String name);

    /**
     * Atomic reservation: succeeds only if enough stock is on hand, in a single
     * round trip — no load-mutate-save race window under concurrent ordering.
     * Returns the number of rows updated (0 or 1).
     */
    @Modifying
    @Transactional
    @Query("UPDATE ProductEntity p SET p.stock = p.stock - :qty WHERE p.id = :id AND p.stock >= :qty")
    int reserveStock(@Param("id") String id, @Param("qty") int qty);

    /** Returns stock to the pool (release) or tops it up (restock) — same add-only operation. */
    @Modifying
    @Transactional
    @Query("UPDATE ProductEntity p SET p.stock = p.stock + :qty WHERE p.id = :id")
    int addStock(@Param("id") String id, @Param("qty") int qty);
}
