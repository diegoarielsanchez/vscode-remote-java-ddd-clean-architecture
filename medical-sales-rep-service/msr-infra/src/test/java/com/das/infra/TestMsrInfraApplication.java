package com.das.infra;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot bootstrap required by {@code @DataJpaTest} when the module
 * contains no {@code @SpringBootApplication} class of its own (library module).
 */
@SpringBootApplication
class TestMsrInfraApplication {
}
