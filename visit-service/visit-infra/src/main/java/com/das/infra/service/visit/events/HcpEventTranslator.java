package com.das.infra.service.visit.events;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Anti-corruption layer for the {@code hcp.events} contract (schema version 1).
 *
 * <p>Events are untrusted input (OWASP A03/A08): the raw bytes are parsed as a JSON tree (no
 * polymorphic or header-driven type resolution), size-limited, and every field the handler uses is
 * validated. Unknown extra fields are ignored so producers can add fields compatibly; an unknown
 * {@code schemaVersion} or {@code eventType} is rejected.
 */
@Component
public class HcpEventTranslator {

    static final int MAX_MESSAGE_BYTES = 16 * 1024;
    static final int MAX_NAME_LENGTH = 100;
    static final int SUPPORTED_SCHEMA_VERSION = 1;

    private static final Map<String, HcpChange.Type> TYPES = Map.of(
            "hcp.created", HcpChange.Type.CREATED,
            "hcp.updated", HcpChange.Type.UPDATED,
            "hcp.activated", HcpChange.Type.ACTIVATED,
            "hcp.deactivated", HcpChange.Type.DEACTIVATED);

    private final ObjectMapper json = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    public HcpChange translate(byte[] body) {
        if (body == null || body.length == 0) throw invalid("empty message");
        if (body.length > MAX_MESSAGE_BYTES) throw invalid("message exceeds " + MAX_MESSAGE_BYTES + " bytes");

        JsonNode root;
        try {
            root = json.readTree(body);
        } catch (IOException e) {
            throw invalid("malformed JSON");
        }
        if (root == null || !root.isObject()) throw invalid("message is not a JSON object");

        JsonNode schema = root.get("schemaVersion");
        if (schema == null || !schema.canConvertToInt()) throw invalid("schemaVersion is missing");
        if (schema.intValue() != SUPPORTED_SCHEMA_VERSION) throw invalid("unsupported schemaVersion");

        UUID eventId = uuid(requiredText(root, "eventId"), "eventId");
        HcpChange.Type type = TYPES.get(requiredText(root, "eventType"));
        if (type == null) throw invalid("unsupported eventType");
        String hcpId = requiredText(root, "aggregateId");
        uuid(hcpId, "aggregateId"); // HCP ids are UUIDs

        JsonNode version = root.get("aggregateVersion");
        if (version == null || !version.canConvertToLong() || version.longValue() < 1) {
            throw invalid("aggregateVersion must be a positive integer");
        }

        JsonNode data = root.get("data");
        if (data == null || !data.isObject()) throw invalid("data is missing");

        return switch (type) {
            case CREATED, UPDATED -> new HcpChange(eventId, type, hcpId, version.longValue(),
                    name(data, "name"), name(data, "surname"), requiredBoolean(data, "active"));
            case ACTIVATED -> new HcpChange(eventId, type, hcpId, version.longValue(), null, null, true);
            case DEACTIVATED -> new HcpChange(eventId, type, hcpId, version.longValue(), null, null, false);
        };
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) throw invalid(field + " is missing");
        return value.asText();
    }

    private static String name(JsonNode data, String field) {
        String value = requiredText(data, field).strip();
        if (value.length() > MAX_NAME_LENGTH) throw invalid(field + " exceeds " + MAX_NAME_LENGTH + " characters");
        if (value.chars().anyMatch(Character::isISOControl)) throw invalid(field + " contains control characters");
        return value;
    }

    private static boolean requiredBoolean(JsonNode data, String field) {
        JsonNode value = data.get(field);
        if (value == null || !value.isBoolean()) throw invalid(field + " must be a boolean");
        return value.booleanValue();
    }

    private static UUID uuid(String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw invalid(field + " is not a UUID");
        }
    }

    private static InvalidIntegrationEventException invalid(String reason) {
        return new InvalidIntegrationEventException("Invalid hcp event: " + reason);
    }
}
