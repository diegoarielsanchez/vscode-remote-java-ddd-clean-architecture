package com.das.infra.service.assistant.claude;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.das.cleanddd.domain.assistant.ports.AssistantModelVersion;
import com.das.infra.service.assistant.persistence.NonDurableStore;

/**
 * The model, prompt and client the assistant runs with. The client exists only when {@code assistant.enabled=true},
 * so with the assistant off no API key is needed and no call can be made.
 */
@Configuration
public class AssistantModelConfig {

    @Bean
    SystemPrompt systemPrompt(@Value("${assistant.prompt:system-v1}") String name) {
        return SystemPrompt.load(name);
    }

    @Bean
    AssistantModelVersion assistantModelVersion(@Value("${assistant.model.id:claude-opus-5-5}") String modelId,
                                                SystemPrompt systemPrompt) {
        return new AssistantModelVersion(modelId, systemPrompt.version());
    }

    /**
     * The key comes from the environment ({@code ANTHROPIC_API_KEY}) or the secret store, never from a properties
     * file. Startup fails without one rather than failing on the first rep's message.
     */
    @Bean
    @ConditionalOnProperty(name = "assistant.enabled", havingValue = "true")
    AnthropicClient anthropicClient(@Value("${assistant.anthropic.api-key:}") String apiKey,
                                    @Value("${assistant.anthropic.timeout:PT60S}") Duration timeout,
                                    Environment environment, List<NonDurableStore> nonDurableStores) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("assistant.enabled=true needs ANTHROPIC_API_KEY");
        }
        if (Arrays.asList(environment.getActiveProfiles()).contains("prod") && !nonDurableStores.isEmpty()) {
            throw new IllegalStateException("The assistant cannot be enabled in prod while drafts, the audit trail or"
                    + " conversations are kept in memory only: " + nonDurableStores.stream()
                            .map(store -> store.getClass().getSimpleName()).toList());
        }
        return AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(timeout)
                .maxRetries(2)
                .build();
    }
}
