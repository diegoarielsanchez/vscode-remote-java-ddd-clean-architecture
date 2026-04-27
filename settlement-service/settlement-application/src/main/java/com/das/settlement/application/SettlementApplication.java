package com.das.settlement.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(
    scanBasePackages = {
        "com.das.settlement.application",
        "com.das.cleanddd.domain.settlement",
        "com.das.inframySQL.service.settlement"
    }
)
@EnableJpaRepositories(basePackages = "com.das.inframySQL.service.settlement")
@EntityScan(basePackages = "com.das.inframySQL.service.settlement")
public class SettlementApplication {

    public static void main(String[] args) {
        SpringApplication.run(SettlementApplication.class, args);
    }
}
