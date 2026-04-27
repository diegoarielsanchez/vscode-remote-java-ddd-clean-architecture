package com.das.visit.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(
    scanBasePackages = {
        "com.das.visit.application",
        "com.das.cleanddd.domain.visit",
        "com.das.inframySQL.service.visit"
    }
)
@EnableJpaRepositories(basePackages = "com.das.inframySQL.service.visit")
@EntityScan(basePackages = "com.das.inframySQL.service.visit")
public class VisitApplication {

    public static void main(String[] args) {
        SpringApplication.run(VisitApplication.class, args);
    }
}
