package com.das.infra.service.assistant.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A tool's input contract as JSON Schema. Every property is required and no other property is allowed, as strict
 * tool use asks; a value the model may leave out is declared nullable instead. Length and range limits are given
 * in the descriptions and enforced by the tool itself ({@link ToolInputs}), since the schema is not trusted alone.
 */
public record ToolSchema(Map<String, Map<String, Object>> properties) {

    public ToolSchema {
        properties = Map.copyOf(properties);
    }

    public List<String> required() {
        return List.copyOf(properties.keySet());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<String, Map<String, Object>> properties = new LinkedHashMap<>();

        public Builder string(String name, String description) {
            properties.put(name, Map.of("type", "string", "description", description));
            return this;
        }

        public Builder nullableString(String name, String description) {
            properties.put(name, Map.of("type", List.of("string", "null"), "description", description));
            return this;
        }

        public Builder integer(String name, String description) {
            properties.put(name, Map.of("type", "integer", "description", description));
            return this;
        }

        public Builder array(String name, String description, Map<String, Object> items) {
            properties.put(name, Map.of("type", "array", "description", description, "items", items));
            return this;
        }

        public ToolSchema build() {
            return new ToolSchema(properties);
        }
    }
}
