package com.das.infra.service.catalog.events;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** {@link AbstractOrderEventFlowBrokerTest} against a throw-away RabbitMQ container (CI). Skipped without Docker. */
@Testcontainers(disabledWithoutDocker = true)
class OrderEventFlowBrokerTest extends AbstractOrderEventFlowBrokerTest {

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-alpine"));
}
