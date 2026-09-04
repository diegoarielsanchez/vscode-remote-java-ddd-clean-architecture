package com.das.catalog.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(
    scanBasePackages = {
        "com.das.catalog.application",
        "com.das.cleanddd.domain.catalog",
        "com.das.infra.service.catalog"
    }
)
@EnableJpaRepositories(basePackages = "com.das.infra.service.catalog")
@EntityScan(basePackages = "com.das.infra.service.catalog")
public class CatalogApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogApplication.class, args);
    }
}
