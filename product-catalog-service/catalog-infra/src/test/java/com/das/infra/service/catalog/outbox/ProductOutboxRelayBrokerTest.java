package com.das.infra.service.catalog.outbox;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** {@link AbstractProductOutboxRelayBrokerTest} against a throw-away RabbitMQ container (CI). Skipped without Docker. */
@Testcontainers(disabledWithoutDocker = true)
class ProductOutboxRelayBrokerTest extends AbstractProductOutboxRelayBrokerTest {

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-alpine"));
}
