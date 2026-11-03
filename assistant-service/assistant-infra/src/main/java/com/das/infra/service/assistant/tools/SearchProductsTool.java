package com.das.infra.service.assistant.tools;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.das.cleanddd.domain.assistant.ports.ICatalogPort;

import tools.jackson.databind.JsonNode;

@Component
public class SearchProductsTool implements AssistantTool {

    private static final ToolSchema SCHEMA = ToolSchema.builder()
            .string("name", "The product's name or part of it, 2 to 80 characters.")
            .integer("page", "Result page, from 1 to 20. Each page has up to 10 products.")
            .build();

    private final ICatalogPort catalog;

    public SearchProductsTool(ICatalogPort catalog) {
        this.catalog = catalog;
    }

    @Override
    public String name() {
        return "search_products";
    }

    @Override
    public String description() {
        return "Search the product catalog by name. Returns each product's id, name, unit, GS1 GTIN, whether it is"
                + " active, whether it is lot-tracked, and its storage conditions. Always take product ids from"
                + " here; never guess an id or a GTIN.";
    }

    @Override
    public ToolSchema schema() {
        return SCHEMA;
    }

    @Override
    public ToolResult run(JsonNode input, ToolContext context) {
        ToolInputs.onlyFields(input, SCHEMA);
        String name = ToolInputs.text(input, "name", 2, 80);
        int page = ToolInputs.integer(input, "page", 1, 20);
        return ToolResult.of(Map.of("products", catalog.findProductsByName(name, page)));
    }
}
