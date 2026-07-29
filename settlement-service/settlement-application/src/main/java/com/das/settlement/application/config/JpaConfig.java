package com.das.settlement.application.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA repository/entity scanning is kept out of {@code SettlementApplication}
 * so that web-layer slice tests (e.g. {@code @WebMvcTest}) don't pull in JPA
 * infrastructure and require a real {@code entityManagerFactory} bean.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.das.infra.service.settlement")
@EntityScan(basePackages = "com.das.infra.service.settlement")
public class JpaConfig {
}
