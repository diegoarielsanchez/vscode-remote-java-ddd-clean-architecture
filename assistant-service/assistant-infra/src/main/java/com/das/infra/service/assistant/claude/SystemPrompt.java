package com.das.infra.service.assistant.claude;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.das.cleanddd.domain.assistant.audit.AgentAuditEntry;

/**
 * The assistant's system prompt, read from {@code prompts/<name>.md} on the classpath. Its version is the name plus
 * the SHA-256 of the text, so any edit to the prompt shows up as a new version in the audit trail.
 */
public record SystemPrompt(String name, String text) {

    public static SystemPrompt load(String name) {
        String resource = "prompts/" + name + ".md";
        try (InputStream in = SystemPrompt.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("System prompt not found: " + resource);
            }
            return new SystemPrompt(name, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read system prompt " + resource, e);
        }
    }

    public String version() {
        return name + ":" + AgentAuditEntry.sha256(text);
    }
}
