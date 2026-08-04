package com.das.settlement.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
    scanBasePackages = {
        "com.das.settlement.application",
        "com.das.cleanddd.domain.settlement",
        "com.das.infra.service.settlement"
    }
)
public class SettlementApplication {

    public static void main(String[] args) {
        SpringApplication.run(SettlementApplication.class, args);
    }
}
