package com.das.infra.service.visit.events;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Validates the integration-event envelope shared by all upstream contexts (schema version 1)
 * and gives translators typed access to {@code data}.
 *
 * <p>Events are untrusted input (OWASP A03/A08): raw bytes are parsed as a JSON tree (no
 * polymorphic or header-driven type resolution), size-limited, duplicate keys are rejected, and
 * every field used is validated. Unknown extra fields are ignored so producers can add fields
 * compatibly; an unknown {@code schemaVersion} or {@code eventType} is rejected.
 */
final class EnvelopeParser {

    static final int MAX_MESSAGE_BYTES = 16 * 1024;
    static final int MAX_NAME_LENGTH = 100;
    static final int SUPPORTED_SCHEMA_VERSION = 1;

    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    /** A structurally valid envelope; {@code data} is still to be read by the translator. */
    record Envelope(String contract, UUID eventId, String eventType, String aggregateId, long version, JsonNode data) {

        String name(String field) {
            String value = requiredText(contract, data, field).strip();
            if (value.length() > MAX_NAME_LENGTH) throw invalid(contract, field + " exceeds " + MAX_NAME_LENGTH + " characters");
            if (value.chars().anyMatch(Character::isISOControl)) throw invalid(contract, field + " contains control characters");
            return value;
        }

        boolean requiredBoolean(String field) {
            JsonNode value = data.get(field);
            if (value == null || !value.isBoolean()) throw invalid(contract, field + " must be a boolean");
            return value.booleanValue();
        }
    }

    private EnvelopeParser() {}

    /**
     * @param contract   short name used in error messages ("hcp", "msr")
     * @param eventTypes the event types this consumer understands
     */
    static Envelope parse(byte[] body, String contract, Set<String> eventTypes) {
        if (body == null || body.length == 0) throw invalid(contract, "empty message");
        if (body.length > MAX_MESSAGE_BYTES) throw invalid(contract, "message exceeds " + MAX_MESSAGE_BYTES + " bytes");

        JsonNode root;
        try {
            root = JSON.readTree(body);
        } catch (IOException e) {
            throw invalid(contract, "malformed JSON");
        }
        if (root == null || !root.isObject()) throw invalid(contract, "message is not a JSON object");

        JsonNode schema = root.get("schemaVersion");
        if (schema == null || !schema.canConvertToInt()) throw invalid(contract, "schemaVersion is missing");
        if (schema.intValue() != SUPPORTED_SCHEMA_VERSION) throw invalid(contract, "unsupported schemaVersion");

        UUID eventId = uuid(contract, requiredText(contract, root, "eventId"), "eventId");
        String eventType = requiredText(contract, root, "eventType");
        if (!eventTypes.contains(eventType)) throw invalid(contract, "unsupported eventType");
        String aggregateId = requiredText(contract, root, "aggregateId");
        uuid(contract, aggregateId, "aggregateId"); // upstream aggregate ids are UUIDs

        JsonNode version = root.get("aggregateVersion");
        if (version == null || !version.canConvertToLong() || version.longValue() < 1) {
            throw invalid(contract, "aggregateVersion must be a positive integer");
        }

        JsonNode data = root.get("data");
        if (data == null || !data.isObject()) throw invalid(contract, "data is missing");

        return new Envelope(contract, eventId, eventType, aggregateId, version.longValue(), data);
    }

    private static String requiredText(String contract, JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) throw invalid(contract, field + " is missing");
        return value.asText();
    }

    private static UUID uuid(String contract, String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw invalid(contract, field + " is not a UUID");
        }
    }

    private static InvalidIntegrationEventException invalid(String contract, String reason) {
        return new InvalidIntegrationEventException("Invalid " + contract + " event: " + reason);
    }
}
