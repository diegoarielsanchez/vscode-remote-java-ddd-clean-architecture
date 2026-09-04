package com.das.order.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(
    scanBasePackages = {
        "com.das.order.application",
        "com.das.cleanddd.domain.order",
        "com.das.infra.service.order"
    }
)
@EnableJpaRepositories(basePackages = "com.das.infra.service.order")
@EntityScan(basePackages = "com.das.infra.service.order")
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
