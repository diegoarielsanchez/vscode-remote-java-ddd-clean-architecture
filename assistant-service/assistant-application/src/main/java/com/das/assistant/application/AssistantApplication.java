package com.das.assistant.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
    scanBasePackages = {
        "com.das.assistant.application",
        "com.das.cleanddd.domain.assistant",
        "com.das.infra.service.assistant"
    }
)
public class AssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(AssistantApplication.class, args);
    }
}
