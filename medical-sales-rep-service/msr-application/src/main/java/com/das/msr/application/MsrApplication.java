package com.das.msr.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(
    scanBasePackages = {
        "com.das.msr.application",
        "com.das.cleanddd.domain.medicalsalesrep",
        "com.das.inframySQL.service.medicalsalesrep"
    }
)
@EnableJpaRepositories(basePackages = "com.das.inframySQL.service.medicalsalesrep")
@EntityScan(basePackages = "com.das.inframySQL.service.medicalsalesrep")
public class MsrApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsrApplication.class, args);
    }
}
