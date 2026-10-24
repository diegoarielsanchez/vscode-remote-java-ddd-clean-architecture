package com.das.infra.service.catalog.lot;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.das.cleanddd.domain.catalog.lot.ShelfLifePolicy;

/** For slice tests that wire the stock use cases: the lot repository, no minimum shelf life, and a lot helper. */
@TestConfiguration
@Import(SQLStockLotRepository.class)
public class TestStockLots {

    @Bean
    ShelfLifePolicy shelfLifePolicy() {
        return ShelfLifePolicy.NONE;
    }

    /** Gives the product one far-future lot holding {@code quantity}. */
    public static void addLot(StockLotJpaRepository rows, String productId, int quantity) {
        rows.save(new StockLotEntity(UUID.randomUUID().toString(), productId, "L-" + productId.substring(0, 8),
                LocalDate.of(2099, 12, 31), quantity, "AVAILABLE", Instant.parse("2026-01-01T00:00:00Z")));
    }
}
