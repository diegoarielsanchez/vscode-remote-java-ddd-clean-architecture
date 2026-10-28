package com.das.infra.service.catalog.lot;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface StockLotJpaRepository extends JpaRepository<StockLotEntity, String> {

    List<StockLotEntity> findByProductId(String productId);

    Optional<StockLotEntity> findByProductIdAndBatchNumber(String productId, String batchNumber);

    /**
     * Atomic take: succeeds only while the lot is AVAILABLE and holds enough, in one round trip, so two orders can
     * never take the same units. {@code flushAutomatically}/{@code clearAutomatically}: pending writes of the same
     * transaction (outbox, processed-event rows) are flushed first, and no stale lot stays in the persistence context.
     *
     * @return rows updated, 0 or 1
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("UPDATE StockLotEntity l SET l.quantity = l.quantity - :qty "
            + "WHERE l.id = :id AND l.status = 'AVAILABLE' AND l.quantity >= :qty")
    int take(@Param("id") String id, @Param("qty") int qty);

    /** Atomic add: more units of the batch received, or units given back. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("UPDATE StockLotEntity l SET l.quantity = l.quantity + :qty WHERE l.id = :id")
    int give(@Param("id") String id, @Param("qty") int qty);

    @Query("SELECT COALESCE(SUM(l.quantity), 0) FROM StockLotEntity l WHERE l.productId = :productId AND l.status <> 'REJECTED'")
    int onHand(@Param("productId") String productId);

    /** Units on hand per product, for listing several products in one query. */
    @Query("SELECT l.productId, SUM(l.quantity) FROM StockLotEntity l WHERE l.productId IN :productIds AND l.status <> 'REJECTED' GROUP BY l.productId")
    List<Object[]> onHandByProduct(@Param("productIds") Collection<String> productIds);
}
