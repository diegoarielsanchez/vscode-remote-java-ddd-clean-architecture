package com.das.infra.service.assistant.tools;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.das.cleanddd.domain.assistant.ports.ICatalogPort;

import tools.jackson.databind.JsonNode;

@Component
public class CheckAvailabilityTool implements AssistantTool {

    static final int MAX_QUANTITY = 10_000;

    private static final ToolSchema SCHEMA = ToolSchema.builder()
            .string("productId", "A product id returned by search_products.")
            .integer("quantity", "Units needed, from 1 to " + MAX_QUANTITY + ".")
            .build();

    private final ICatalogPort catalog;

    public CheckAvailabilityTool(ICatalogPort catalog) {
        this.catalog = catalog;
    }

    @Override
    public String name() {
        return "check_availability";
    }

    @Override
    public String description() {
        return "Check whether a product is active and enough deliverable stock exists for a quantity. Answers only"
                + " yes or no; the exact stock is not disclosed.";
    }

    @Override
    public ToolSchema schema() {
        return SCHEMA;
    }

    @Override
    public ToolResult run(JsonNode input, ToolContext context) {
        ToolInputs.onlyFields(input, SCHEMA);
        String productId = ToolInputs.id(input, "productId");
        int quantity = ToolInputs.integer(input, "quantity", 1, MAX_QUANTITY);
        return ToolResult.of(Map.of("available", catalog.isAvailable(productId, quantity)));
    }
}
