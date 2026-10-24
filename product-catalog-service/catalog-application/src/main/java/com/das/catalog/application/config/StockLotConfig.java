package com.das.catalog.application.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.das.cleanddd.domain.catalog.lot.ShelfLifePolicy;

/** How stock is allocated from lots. */
@Configuration
public class StockLotConfig {

    /** Lots expiring within this many days are not delivered (GDP minimum remaining shelf life); 0 = through expiry day. */
    @Bean
    ShelfLifePolicy shelfLifePolicy(@Value("${catalog.stock.min-remaining-shelf-life-days:0}") int minRemainingDays) {
        return new ShelfLifePolicy(minRemainingDays);
    }
}
