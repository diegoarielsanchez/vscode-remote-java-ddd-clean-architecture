package com.das.infra.service.catalog.events;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
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
    static final int MAX_NAME_LENGTH = 200;
    static final int MAX_REASON_LENGTH = 500;
    static final int SUPPORTED_SCHEMA_VERSION = 1;
    private static final java.util.regex.Pattern EVENT_TYPE = java.util.regex.Pattern.compile("[a-z0-9][a-z0-9.-]{0,99}");

    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS) // money: no binary rounding
            .build();

    /** A structurally valid envelope; {@code data} is still to be read by the translator. */
    record Envelope(String contract, UUID eventId, String eventType, String aggregateId, long version, JsonNode data) {

        InvalidIntegrationEventException invalid(String reason) {
            return EnvelopeParser.invalid(contract, reason);
        }

        /** Free text (a name or a reason): trimmed, bounded, no control characters. */
        String text(JsonNode node, String field, int maxLength) {
            String value = requiredText(contract, node, field).strip();
            if (value.length() > maxLength) throw invalid(field + " exceeds " + maxLength + " characters");
            if (value.chars().anyMatch(Character::isISOControl)) throw invalid(field + " contains control characters");
            return value;
        }

        String uuidText(JsonNode node, String field) {
            return uuid(contract, requiredText(contract, node, field), field).toString();
        }

        int positiveInt(JsonNode node, String field) {
            JsonNode value = node.get(field);
            if (value == null || !value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 1) {
                throw invalid(field + " must be a positive integer");
            }
            return value.intValue();
        }

        BigDecimal nonNegativeDecimal(JsonNode node, String field) {
            JsonNode value = node.get(field);
            if (value == null || !value.isNumber()) throw invalid(field + " must be a number");
            BigDecimal decimal;
            try {
                decimal = value.decimalValue();
            } catch (NumberFormatException e) {
                throw invalid(field + " is out of range");
            }
            // At most 15 integer digits and 4 decimals (the price columns' range).
            if (decimal.signum() < 0 || decimal.scale() > 4 || decimal.precision() - decimal.scale() > 15) {
                throw invalid(field + " is out of range");
            }
            return decimal;
        }

        JsonNode array(JsonNode node, String field, int maxSize) {
            JsonNode value = node.get(field);
            if (value == null || !value.isArray() || value.isEmpty()) throw invalid(field + " must be a non-empty array");
            if (value.size() > maxSize) throw invalid(field + " has more than " + maxSize + " items");
            for (JsonNode item : value) {
                if (!item.isObject()) throw invalid(field + " items must be objects");
            }
            return value;
        }
    }

    private EnvelopeParser() {}

    /**
     * @param contract   short name used in error messages (e.g. "order")
     * @param eventTypes the event types this consumer understands; {@code null} accepts any well-formed
     *                   type and leaves the decision to the translator
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
        if (!EVENT_TYPE.matcher(eventType).matches()) throw invalid(contract, "malformed eventType");
        if (eventTypes != null && !eventTypes.contains(eventType)) throw invalid(contract, "unsupported eventType");
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
            UUID uuid = UUID.fromString(value);
            if (!uuid.toString().equalsIgnoreCase(value)) throw invalid(contract, field + " is not a UUID");
            return uuid;
        } catch (IllegalArgumentException e) {
            throw invalid(contract, field + " is not a UUID");
        }
    }

    private static InvalidIntegrationEventException invalid(String contract, String reason) {
        return new InvalidIntegrationEventException("Invalid " + contract + " event: " + reason);
    }
}
