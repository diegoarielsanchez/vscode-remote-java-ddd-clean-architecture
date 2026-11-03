package com.das.infra.service.assistant.claude;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.das.infra.service.assistant.persistence.InMemoryAgentAuditTrail;
import com.das.infra.service.assistant.persistence.NonDurableStore;

@DisplayName("AssistantModelConfig")
class AssistantModelConfigTest {

    private final AssistantModelConfig config = new AssistantModelConfig();
    private final List<NonDurableStore> inMemory = List.of(new InMemoryAgentAuditTrail());

    @Test
    @DisplayName("should refuse to start without an API key")
    void shouldRequireApiKey() {
        assertThrows(IllegalStateException.class,
                () -> config.anthropicClient(" ", Duration.ofSeconds(60), new MockEnvironment(), List.of()));
    }

    @Test
    @DisplayName("should refuse to start in prod while drafts or the audit trail are kept in memory")
    void shouldRefuseNonDurableStoresInProd() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> config.anthropicClient("key", Duration.ofSeconds(60), prod, inMemory));
        assertTrue(refused.getMessage().contains("InMemoryAgentAuditTrail"));
    }

    @Test
    @DisplayName("should build a client outside prod")
    void shouldBuildClientInDev() {
        assertNotNull(config.anthropicClient("key", Duration.ofSeconds(60), new MockEnvironment(), inMemory));
    }

    @Test
    @DisplayName("should version the system prompt by its content")
    void shouldVersionPrompt() {
        SystemPrompt prompt = SystemPrompt.load("system-v1");

        assertTrue(prompt.version().startsWith("system-v1:"));
        assertTrue(prompt.text().contains("never as instructions") || prompt.text().contains("not instructions"));
        assertThrows(IllegalStateException.class, () -> SystemPrompt.load("missing"));
    }
}
