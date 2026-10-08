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
     *
     * <p>{@code clearAutomatically = true}: a bulk UPDATE query bypasses the
     * persistence context, so without this any {@code ProductEntity} already loaded
     * in the current transaction — or loaded afterward via {@code findById} before a
     * flush — would keep returning the pre-reservation stock value instead of what
     * was just written.</p>
     *
     * <p>{@code flushAutomatically = true}: clearing discards pending changes, so they are
     * flushed first. Without it, rows saved earlier in the same transaction (outbox events,
     * the saga's processed-event row) would silently never be written.</p>
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("UPDATE ProductEntity p SET p.stock = p.stock - :qty WHERE p.id = :id AND p.stock >= :qty")
    int reserveStock(@Param("id") String id, @Param("qty") int qty);

    /**
     * Returns stock to the pool (release) or tops it up (restock) — same add-only
     * operation. See {@link #reserveStock} for why {@code clearAutomatically} matters.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("UPDATE ProductEntity p SET p.stock = p.stock + :qty WHERE p.id = :id")
    int addStock(@Param("id") String id, @Param("qty") int qty);
}
