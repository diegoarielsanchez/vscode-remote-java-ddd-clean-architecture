package com.das.cleanddd.domain.assistant.ports;

import java.util.Objects;

/**
 * Which model and which system prompt the assistant runs with, recorded in every audit entry. Changing either is a
 * change to a validated configuration (GAMP 5), so both are pinned in configuration, never resolved at run time.
 *
 * @param promptVersion e.g. {@code system-v1:3f2a…}, the prompt's name and the SHA-256 of its text
 */
public record AssistantModelVersion(String modelId, String promptVersion) {

    public AssistantModelVersion {
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(promptVersion, "promptVersion");
    }
}
