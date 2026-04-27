package com.das.hcp.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(
    scanBasePackages = {
        "com.das.hcp.application",
        "com.das.cleanddd.domain.healthcareprof",
        "com.das.infrapostgresql.service.healthcareprof"
    }
)
@EnableJpaRepositories(basePackages = "com.das.infrapostgresql.service.healthcareprof")
@EntityScan(basePackages = "com.das.infrapostgresql.service.healthcareprof")
public class HcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(HcpApplication.class, args);
    }
}
