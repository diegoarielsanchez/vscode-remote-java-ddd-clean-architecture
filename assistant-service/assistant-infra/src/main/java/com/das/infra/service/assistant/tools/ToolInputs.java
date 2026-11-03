package com.das.infra.service.assistant.tools;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

/**
 * Server-side checks of a tool's input. The model's input is untrusted: these run even though the API already
 * validated it against the strict schema.
 */
public final class ToolInputs {

    private ToolInputs() {
    }

    /** Rejects any field the contract does not declare (the server-side twin of {@code additionalProperties: false}). */
    public static void onlyFields(JsonNode input, ToolSchema schema) {
        if (input == null || !input.isObject()) {
            throw new ToolInputException("The input must be a JSON object.");
        }
        Set<String> unexpected = new HashSet<>(input.propertyNames());
        unexpected.removeAll(schema.properties().keySet());
        if (!unexpected.isEmpty()) {
            throw new ToolInputException("Unexpected fields: " + unexpected);
        }
    }

    public static String text(JsonNode input, String field, int minLength, int maxLength) {
        JsonNode value = input.get(field);
        if (value == null || !value.isString()) {
            throw new ToolInputException(field + " must be a string.");
        }
        String text = value.asString().strip();
        if (text.length() < minLength || text.length() > maxLength) {
            throw new ToolInputException(field + " must be " + minLength + " to " + maxLength + " characters.");
        }
        return text;
    }

    /** {@code null} when the field is missing or JSON null. */
    public static String nullableText(JsonNode input, String field, int maxLength) {
        JsonNode value = input.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return text(input, field, 0, maxLength);
    }

    public static int integer(JsonNode input, String field, int min, int max) {
        JsonNode value = input.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new ToolInputException(field + " must be an integer.");
        }
        int number = value.asInt();
        if (number < min || number > max) {
            throw new ToolInputException(field + " must be between " + min + " and " + max + ".");
        }
        return number;
    }

    /** An id issued by one of the services: always a UUID, so anything else is rejected before any call. */
    public static String id(JsonNode input, String field) {
        String id = text(input, field, 36, 36);
        try {
            UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ToolInputException(field + " must be an id returned by another tool.");
        }
        return id;
    }

    public static String nullableId(JsonNode input, String field) {
        JsonNode value = input.get(field);
        return value == null || value.isNull() ? null : id(input, field);
    }
}
