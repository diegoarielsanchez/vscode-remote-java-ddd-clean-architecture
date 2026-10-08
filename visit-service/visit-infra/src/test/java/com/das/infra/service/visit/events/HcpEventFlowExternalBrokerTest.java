package com.das.infra.service.visit.events;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * {@link AbstractHcpEventFlowBrokerTest} against an existing RabbitMQ, e.g. the local Docker one when this
 * environment has no Docker daemon of its own. Enabled only when {@code EDA_TEST_RABBITMQ_HOST}
 * is set; use a dedicated virtual host so test queues never mix with the real ones:
 * <pre>
 * EDA_TEST_RABBITMQ_HOST=172.17.0.1 EDA_TEST_RABBITMQ_VHOST=eda-test \
 * EDA_TEST_RABBITMQ_USERNAME=eda_test EDA_TEST_RABBITMQ_PASSWORD=... ./mvnw test
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "EDA_TEST_RABBITMQ_HOST", matches = ".+")
class HcpEventFlowExternalBrokerTest extends AbstractHcpEventFlowBrokerTest {

    @DynamicPropertySource
    static void externalBroker(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", () -> System.getenv("EDA_TEST_RABBITMQ_HOST"));
        registry.add("spring.rabbitmq.port", () -> env("EDA_TEST_RABBITMQ_PORT", "5672"));
        registry.add("spring.rabbitmq.virtual-host", () -> env("EDA_TEST_RABBITMQ_VHOST", "eda-test"));
        registry.add("spring.rabbitmq.username", () -> System.getenv("EDA_TEST_RABBITMQ_USERNAME"));
        registry.add("spring.rabbitmq.password", () -> System.getenv("EDA_TEST_RABBITMQ_PASSWORD"));
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
