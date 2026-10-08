package com.das.infra.service.settlement.events;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** {@link AbstractMsrEventFlowBrokerTest} against a throw-away RabbitMQ container (CI). Skipped without Docker. */
@Testcontainers(disabledWithoutDocker = true)
class MsrEventFlowBrokerTest extends AbstractMsrEventFlowBrokerTest {

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-alpine"));
}
