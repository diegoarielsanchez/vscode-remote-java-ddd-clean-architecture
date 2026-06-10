package com.das.hcp.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(
    scanBasePackages = {
        "com.das.hcp.application",
        "com.das.cleanddd.domain.healthcareprof",
        "com.das.infra.service.healthcareprof"
    }
)
@EnableJpaRepositories(basePackages = "com.das.infra.service.healthcareprof")
@EntityScan(basePackages = "com.das.infra.service.healthcareprof")
public class HcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(HcpApplication.class, args);
    }
}
